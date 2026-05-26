/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.summerboot.jexpress.controller.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.webserver.netty.NioConfig;
import org.summerboot.jexpress.controller.authenticate.Caller;
import org.summerboot.jexpress.util.BeanUtil;
import org.summerboot.jexpress.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    protected static final TextWebSocketFrame MSG_AUTH_FAILED = new TextWebSocketFrame("401 Unauthorized");

    protected static final AttributeKey<FileChannel> FILE_CHANNEL_KEY = AttributeKey.valueOf("fileChannel");
    protected static final AttributeKey<FileOutputStream> FILE_STREAM_KEY = AttributeKey.valueOf("fileStream");
    protected static final AttributeKey<Long> FILE_SIZE_KEY = AttributeKey.valueOf("fileSize");

    // Used to remember the physical file object currently being written in the connection context.
    protected static final AttributeKey<File> TARGET_FILE_KEY = AttributeKey.valueOf("targetFile");
    protected static final AttributeKey<String> TARGET_FILE_NAME_KEY = AttributeKey.valueOf("fileName");

    protected static final AttributeKey<String> ROOM_ID_KEY = AttributeKey.valueOf("chatRoom");

    public static final String CHAT_MSG_FORMAT = "{\"status\":\"SEND\",\"msg\":\"%s\",\"num\":%d ID of chat group/room}";

    // 核心：Key 是房间 ID，Value 是该房间专属的 ChannelGroup
    private static final ConcurrentHashMap<String, ChannelGroup> rooms = new ConcurrentHashMap<>();

    protected Logger log = LogManager.getLogger(this.getClass());


    /**
     * 获取某个聊天室的所有在线通道
     */
    public static ChannelGroup getRoomChannels(String roomId) {
        // 如果房间不存在，自动创建一个新的 ChannelGroup
        return rooms.computeIfAbsent(roomId, k ->
                new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
        );
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Runnable asyncTask = () -> {
            Caller caller = ctx.channel().attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get();
            if (caller == null) {
                // It's unlikely you'll encounter this; handlerAdded has already handled it. But we check again to be safe, as the auth process is asynchronous and may not have completed yet.
                ctx.writeAndFlush(MSG_AUTH_FAILED.retainedDuplicate());
                ctx.close();
                log.warn("OTT auth failed - " + ctx.channel().remoteAddress() + ": " + ctx);
                return;
            }
            log.trace(() -> "User connected: " + caller.getId() + " from " + ctx.channel().remoteAddress());
        };
        NioConfig.cfg.getBizExecutor().execute(asyncTask);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        Caller caller = ctx.channel().attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get();
        if (caller == null) {
            ctx.writeAndFlush(MSG_AUTH_FAILED.retainedDuplicate());
            ctx.close();
            log.warn("OTT auth failed - " + ctx.channel().remoteAddress() + ": " + ctx);
            return;
        }

        // 1. [Special Frame] The client sends a text message to indicate that the transmission is complete.
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            String text = textFrame.text();
            WsControl ctrl = parseCommand(ctx, text, caller);
            if (ctrl == null) {
                return;
            }
            String textMessage = ctrl.getMsg();
            switch (ctrl.getStatus()) {
                case UPLOAD_CLIENT_START -> ctx.channel().attr(TARGET_FILE_NAME_KEY).set(textMessage); // save file name before upload start
                case UPLOAD_CLIENT_COMPLETE -> fileUploadOnReceviedFullFile(ctx, caller);
                case CONNECT -> {
                    processUserConnect(ctx, caller);
                }
                case DISCONNECT -> {
                    ctx.close();
                }
                case SUBSCRIBE -> {
                    String roomId = ctrl.getMsg();
                    processSubscribe(ctx, roomId, caller, true);
                }
                case UNSUBSCRIBE -> {
                    String roomId = ctrl.getMsg();
                    processSubscribe(ctx, roomId, caller, false);
                }
                case SEND -> {
                    Runnable asyncTask = () -> {
                        String roomId = ctx.channel().attr(ROOM_ID_KEY).get();
                        if (roomId == null) {
                            roomId = getDefaultRoomId();
                        }
                        // Handle any custom messages if needed
                        String responseText = onMessageReceived(ctx, roomId, caller, textMessage);
                        if (responseText != null) {
                            broadcast(responseText, roomId);
                        }
                    };
                    NioConfig.cfg.getBizExecutor().execute(asyncTask);
                }
                default -> {
                    WsControl errorCtrl = new WsControl(WsControl.Status.ERROR, "Invalid control status, expected:"
                            + WsControl.Status.UPLOAD_CLIENT_START + ", " + WsControl.Status.UPLOAD_CLIENT_COMPLETE + ", " + WsControl.Status.SUBSCRIBE + ", " + WsControl.Status.SEND
                            + ", received:" + ctrl.getStatus());
                    String jsonError = BeanUtil.toJson(errorCtrl, false, true);
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(jsonError));
                }
            }

        }

        // 2. [Start Frame] The first fragment of the file received.
        if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame startFrame = (BinaryWebSocketFrame) frame;

            FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).get();

            // Initialize on first frame only
            if (fileChannel == null) {
                String fileName = ctx.channel().attr(TARGET_FILE_NAME_KEY).getAndSet(null);
                Path targetPath = Paths.get(NioConfig.instance(NioConfig.class).getTempUoloadDir(), String.valueOf(caller.getId()), fileName).toAbsolutePath();
                Path parent = targetPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                File targetFile = targetPath.toFile();
                boolean append = false;
                FileOutputStream fos = new FileOutputStream(targetFile, append);
                fileChannel = fos.getChannel();

                ctx.channel().attr(TARGET_FILE_KEY).set(targetFile);
                ctx.channel().attr(FILE_STREAM_KEY).set(fos);
                ctx.channel().attr(FILE_CHANNEL_KEY).set(fileChannel);
            }

            // Write and send ACK
            long currentSize = fileUploadWriteAndGetNewSize(startFrame.content(), fileChannel);
            ctx.channel().attr(FILE_SIZE_KEY).set(currentSize);

            // IMPORTANT: Do NOT check isFinalFragment() here - each ws.send() arrives as a complete frame!
            // Instead, the client must signal completion separately or the server must track expected size
            fileUploadSendServerProcess(ctx, currentSize, false);  // Always false for intermediate chunks
        }

        // 3. [Continuous Frames] Received numerous subsequent fragments
        else if (frame instanceof ContinuationWebSocketFrame) {
            ContinuationWebSocketFrame continuationFrame = (ContinuationWebSocketFrame) frame;
            FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).get();

            if (fileChannel == null) {
                ctx.close();
                return;
            }

            long currentSize = fileUploadWriteAndGetNewSize(continuationFrame.content(), fileChannel);
            ctx.channel().attr(FILE_SIZE_KEY).set(currentSize);

            fileUploadSendServerProcess(ctx, currentSize, continuationFrame.isFinalFragment());

            if (continuationFrame.isFinalFragment()) {
                fileUploadOnReceviedFullFile(ctx, caller);
            }
        }
    }

    protected WsControl parseCommand(ChannelHandlerContext ctx, String text, Caller caller) {
        WsControl ctrl = null;
        try {
            ctrl = BeanUtil.fromJson(WsControl.class, text);
            log.debug(() -> "Received msg from " + caller + ": " + text);
            return ctrl;
        } catch (Exception ex) {
            log.warn(() -> "Received invalid msg from " + caller + ": " + text);
            // Handle parsing errors or unexpected messages
            WsControl errorCtrl = new WsControl(WsControl.Status.ERROR, "Invalid control message format, expected:" + CHAT_MSG_FORMAT + ", received:" + text);
            String jsonError = BeanUtil.toJson(errorCtrl, false, true);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(jsonError));
            return null;
        }
    }

    protected String getDefaultRoomId() {
        return "default";
    }

    protected void pong(ChannelHandlerContext ctx) {
        WsControl ctl = new WsControl(WsControl.Status.CONNECTED);
        String jsonAck = BeanUtil.toJson(ctl, false, true);
        ctx.channel().writeAndFlush(new TextWebSocketFrame(jsonAck));
    }

    protected void processUserConnect(ChannelHandlerContext ctx, Caller caller) {
        Runnable asyncTask = () -> {
            pong(ctx);
        };
        NioConfig.cfg.getBizExecutor().execute(asyncTask);
    }

    protected void processSubscribe(ChannelHandlerContext ctx, String roomId, Caller caller, boolean isSubscribe) {
        if (roomId == null) {
            WsControl errorCtrl = new WsControl(WsControl.Status.ERROR, "Missing room ID");
            String jsonError = BeanUtil.toJson(errorCtrl, false, true);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(jsonError));
            return;
        }

        if (isSubscribe) {
            ctx.channel().attr(ROOM_ID_KEY).set(roomId);
        } else {
            ctx.channel().attr(ROOM_ID_KEY).set(null);
        }
        // 1. 获取（或创建）该房间的 ChannelGroup
        ChannelGroup roomChannels = getRoomChannels(roomId);

        // 2. 将当前用户的 Channel 键入该群组
        if (isSubscribe) {
            roomChannels.add(ctx.channel());
        } else {
            roomChannels.remove(ctx.channel());
        }


        // 3. 此时该 Channel 就被安全地保存在 ChatRoomManager 中了
        Runnable asyncTask = () -> {
            log.trace(() -> "User joined group: " + caller.getId() + " from " + ctx.channel().remoteAddress() + ", groupId = " + roomId);
            String message = onCallerSubscribe(ctx, roomId, caller, isSubscribe);
            if (message != null) {
                broadcast(message, roomChannels);
            }
        };
        NioConfig.cfg.getBizExecutor().execute(asyncTask);
    }

    private long fileUploadWriteAndGetNewSize(ByteBuf content, FileChannel fileChannel) throws IOException {
        long position = fileChannel.size();
        content.readBytes(fileChannel, position, content.readableBytes());
        return fileChannel.size();
    }

    private void fileUploadSendServerProcess(ChannelHandlerContext ctx, long uploadedSize, boolean isFinished) throws IOException {
        Map<String, Object> ackMap = new HashMap<>();
        WsControl ctl = new WsControl(isFinished
                ? WsControl.Status.UPLOAD_SERVER_RECEIVED_FULL
                : WsControl.Status.UPLOAD_SERVER_RECEIVED_CHUNK, uploadedSize);

        String jsonAck = BeanUtil.toJson(ctl, false, true);//objectMapper.writeValueAsString(ackMap);
        ctx.channel().writeAndFlush(new TextWebSocketFrame(jsonAck));
    }

    /**
     * Shut down resources, perform disk cleanup, and finally trigger the lifecycle cleanup hook.
     */
    private void fileUploadOnReceviedFullFile(ChannelHandlerContext ctx, Caller caller) throws IOException {
        FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).getAndSet(null);
        FileOutputStream fos = ctx.channel().attr(FILE_STREAM_KEY).getAndSet(null);
        File uploadedFile = ctx.channel().attr(TARGET_FILE_KEY).getAndSet(null);

        if (fileChannel != null) {
            // 极其重要：强制将操作系统缓存区的数据刷入物理存储介质
            fileChannel.force(true);
            fileChannel.close();
        }
        if (fos != null) {
            fos.close();
        }

        // ==========================================
        // [Core Trigger Point] At this point, the file has been completely written to disk, allowing for a safe invocation of the business cleanup method.
        // ==========================================
        if (uploadedFile != null && uploadedFile.exists()) {
            String roomID = ctx.channel().attr(ROOM_ID_KEY).get();
            String roomId = roomID == null ? getDefaultRoomId() : roomID;

            Runnable asyncTask = () -> {
                // 1. audit file
                String error = auditReceivedFile(uploadedFile);
                WsControl ctl = error == null
                        ? new WsControl(WsControl.Status.UPLOAD_SERVER_AUDIT_COMPLETE)
                        : new WsControl(WsControl.Status.UPLOAD_SERVER_AUDIT_FAILED, error);
                String jsonAck = BeanUtil.toJson(ctl, false, true);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(jsonAck));
                if (error != null) {
                    log.warn("File audit failed: " + error + ",file=" + uploadedFile.getAbsolutePath());
                    return;
                }

                // 2. get file info
                FileUtil.FileTypeInfo detectMimeType = null;
                try (InputStream inputStream = Files.newInputStream(uploadedFile.toPath())) {
                    detectMimeType = FileUtil.detectMimeType(inputStream);
                } catch (Throwable ex) {
                    detectMimeType = new FileUtil.FileTypeInfo("", "", "");
                    log.error("Failed to detectMimeType: " + uploadedFile, ex);
                }

                // 3. process file
                StringBuilder message = new StringBuilder();
                boolean broadcast = onFileRecevied(ctx, roomId, caller, uploadedFile, detectMimeType, message);
                if (broadcast) {
                    if (!message.isEmpty()) {
                        broadcast(message.toString(), roomId);
                    }
                    broadcast(uploadedFile, detectMimeType, roomId);
                }
            };
            NioConfig.cfg.getBizExecutor().execute(asyncTask);
        }
    }

    protected String auditReceivedFile(File uploadedFile) {
        return null;
    }

    abstract protected String onCallerSubscribe(ChannelHandlerContext ctx, String roomId, Caller caller, boolean isSubscribe);

    /**
     * @param ctx
     * @param caller
     * @param txt
     * @return non-null string will send back to peer
     */
    abstract protected String onMessageReceived(ChannelHandlerContext ctx, String roomId, Caller caller, String txt);

    /**
     * Business callback hook after successful lossless transfer and disk write of large files
     */
    abstract protected boolean onFileRecevied(ChannelHandlerContext ctx, String roomId, Caller caller, File targetFile, FileUtil.FileTypeInfo detectMimeType, StringBuilder message);

    final protected void broadcast(String text, String roomId) {
        this.broadcast(text, getRoomChannels(roomId));
    }

    protected void broadcast(String text, ChannelGroup recipients) {
        TextWebSocketFrame message = buildTextMessage(text);
        recipients.writeAndFlush(message);
    }

    protected TextWebSocketFrame buildTextMessage(String text) {
        WsControl ctl = new WsControl(WsControl.Status.MESSAGE, text);
        String json = BeanUtil.toJson(ctl, false, true);
        return new TextWebSocketFrame(json);
    }

    final protected void broadcast(File file, FileUtil.FileTypeInfo detectMimeType, String roomId) {
        this.broadcast(file, detectMimeType, getRoomChannels(roomId));
    }

    public static long FIVE_MB = 5 * 1024 * 1024;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    protected void broadcast(File file, FileUtil.FileTypeInfo detectMimeType, ChannelGroup recipients) {
        WsControl ctl = new WsControl(WsControl.Status.FILE);
        ctl.setFileExtension(detectMimeType.getExtension());
        ctl.setFileType(detectMimeType.getGroup());
        ctl.setMimeType(detectMimeType.getMimeType());
        ctl.setFileName(file.getName());
        String jsonAck = BeanUtil.toJson(ctl, false, true);
        recipients.writeAndFlush(new TextWebSocketFrame(jsonAck));


        Long fileSize = file.length();
        if (fileSize < FIVE_MB) {
            // [Option A] Small files: Directly broadcast binary data
            try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
                ByteBuf byteBuf;
                if (IS_WINDOWS) {
                    byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(fileSize.intValue());
                    byteBuf.writeBytes(channel, fileSize.intValue());
                } else {
                    /*
                    Use MappedByteBuffer for extremely fast reading, or use a small loop to read, avoiding allocating too large a byte array at once.
                    But it has a Windows-specific issue that occurs when you try to modify or delete a file that is still mapped into memory by a Java process (typically via MappedByteBuffer) or another application. On Windows, a file lock is maintained as long as the memory mapping exists, and Java does not provide a direct unmap() method to release it immediately.
                     */
                    MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
                    // 构造 Netty 的 ByteBuf（注意：这里可以用 Unpooled 包装，也可以用 Pooled）
                    byteBuf = Unpooled.wrappedBuffer(buffer);
                }

                // Broadcast to all channels in the chat room
                recipients.writeAndFlush(new BinaryWebSocketFrame(byteBuf));

                // After the broadcast is complete, local temporary files can be deleted asynchronously.
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (Exception e) {
                    log.error("Asynchronous deletion of temporary files failed: " + e.getMessage());
                }
            } catch (IOException ex) {
                // 异常处理
                log.error("Failed to broadcast file: " + file.getAbsolutePath(), ex);
            }
        } else {
            // TODO [Option B] Large Files: Move Files and Generate Links
            // 1. Asynchronously move the file to a public static resource directory or upload it to distributed storage (such as MinIO).
            /*String downloadUrl = fileStorageService.moveToStorageAndGetUrl(filePath);

            // 2.
            String jsonMessage = String.format(
                    "{\"type\":\"FILE_LINK\",\"sender\":\"%s\",\"url\":\"%s\",\"size\":%d}",
                    senderId, downloadUrl, fileSize
            );

            // 3.
            ChannelGroup recipients = ChatRoomManager.getRoomChannels(roomId);
            recipients.writeAndFlush(new TextWebSocketFrame(jsonMessage));*/
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("exceptionCaught", cause);
        FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).getAndSet(null);
        FileOutputStream fos = ctx.channel().attr(FILE_STREAM_KEY).getAndSet(null);

        if (fileChannel != null) fileChannel.close();
        if (fos != null) fos.close();

        ctx.close();
    }


}

