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


package org.summerboot.jexpress.nio.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.security.auth.Caller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class LargeFileStreamHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final AttributeKey<FileChannel> FILE_CHANNEL_KEY = AttributeKey.valueOf("fileChannel");
    private static final AttributeKey<FileOutputStream> FILE_STREAM_KEY = AttributeKey.valueOf("fileStream");
    private static final AttributeKey<Long> FILE_SIZE_KEY = AttributeKey.valueOf("fileSize");

    // Used to remember the physical file object currently being written in the connection context.
    private static final AttributeKey<File> TARGET_FILE_KEY = AttributeKey.valueOf("targetFile");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        Caller caller = ctx.channel().attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get();
        String userId = caller == null ? "anonymous" : caller.getUid();

        // 1. 【起始帧】收到文件的第一个分片
        if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame startFrame = (BinaryWebSocketFrame) frame;

            FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).get();

            // Initialize on first frame only
            if (fileChannel == null) {
                Path targetPath = Paths.get(NioConfig.instance(NioConfig.class).getTempUoloadDir(), String.valueOf(caller.getId()), System.currentTimeMillis() + ".dat").toAbsolutePath();
                Path parent = targetPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                File targetFile = targetPath.toFile();
                FileOutputStream fos = new FileOutputStream(targetFile, true);
                fileChannel = fos.getChannel();

                ctx.channel().attr(TARGET_FILE_KEY).set(targetFile);
                ctx.channel().attr(FILE_STREAM_KEY).set(fos);
                ctx.channel().attr(FILE_CHANNEL_KEY).set(fileChannel);
            }

            // Write and send ACK
            long currentSize = writeAndGetNewSize(startFrame.content(), fileChannel);
            ctx.channel().attr(FILE_SIZE_KEY).set(currentSize);

            // IMPORTANT: Do NOT check isFinalFragment() here - each ws.send() arrives as a complete frame!
            // Instead, the client must signal completion separately or the server must track expected size
            sendAck(ctx, currentSize, false);  // Always false for intermediate chunks
        }

        // 2. [Continuous Frames] Received numerous subsequent fragments
        else if (frame instanceof ContinuationWebSocketFrame) {
            ContinuationWebSocketFrame continuationFrame = (ContinuationWebSocketFrame) frame;
            FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).get();

            if (fileChannel == null) {
                ctx.close();
                return;
            }

            long currentSize = writeAndGetNewSize(continuationFrame.content(), fileChannel);
            ctx.channel().attr(FILE_SIZE_KEY).set(currentSize);

            sendAck(ctx, currentSize, continuationFrame.isFinalFragment());

            if (continuationFrame.isFinalFragment()) {
                closeAndCleanUp(ctx, caller);
            }
        }

        // 3. [Special Frame] The client sends a text message to indicate that the transmission is complete.
        else if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            String text = textFrame.text();

            if ("UPLOAD_COMPLETE".equals(text)) {
                closeAndCleanUp(ctx, caller);
            }
        }
    }

    private long writeAndGetNewSize(ByteBuf content, FileChannel fileChannel) throws IOException {
        long position = fileChannel.size();
        content.readBytes(fileChannel, position, content.readableBytes());
        return fileChannel.size();
    }

    private void sendAck(ChannelHandlerContext ctx, long uploadedSize, boolean isFinished) throws IOException {
        Map<String, Object> ackMap = new HashMap<>();
        ackMap.put("status", isFinished ? "COMPLETE" : "PROGRESS");
        ackMap.put("uploadedSize", uploadedSize);

        String jsonAck = objectMapper.writeValueAsString(ackMap);
        ctx.channel().writeAndFlush(new TextWebSocketFrame(jsonAck));
    }

    /**
     * Shut down resources, perform disk cleanup, and finally trigger the lifecycle cleanup hook.
     */
    private void closeAndCleanUp(ChannelHandlerContext ctx, Caller caller) throws IOException {
        FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).getAndSet(null);
        FileOutputStream fos = ctx.channel().attr(FILE_STREAM_KEY).getAndSet(null);
        File targetFile = ctx.channel().attr(TARGET_FILE_KEY).getAndSet(null);

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
        if (targetFile != null && targetFile.exists()) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"status\":\"ALL_TASKS_COMPLETE\"}"));
            onUploadCompleted(ctx, targetFile, caller);
        }
    }

    /**
     * Business callback hook after successful lossless transfer and disk write of large files
     */
    abstract protected void onUploadCompleted(ChannelHandlerContext ctx, File targetFile, Caller caller);


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).getAndSet(null);
        FileOutputStream fos = ctx.channel().attr(FILE_STREAM_KEY).getAndSet(null);

        if (fileChannel != null) fileChannel.close();
        if (fos != null) fos.close();

        ctx.close();
    }
}

