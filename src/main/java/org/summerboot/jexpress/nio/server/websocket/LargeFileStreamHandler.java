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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

public class LargeFileStreamHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final AttributeKey<FileChannel> FILE_CHANNEL_KEY = AttributeKey.valueOf("fileChannel");
    private static final AttributeKey<FileOutputStream> FILE_STREAM_KEY = AttributeKey.valueOf("fileStream");
    private static final AttributeKey<Long> FILE_SIZE_KEY = AttributeKey.valueOf("fileSize");

    // 新增：用于在连接上下文中记住当前正在写入的物理文件对象
    private static final AttributeKey<File> TARGET_FILE_KEY = AttributeKey.valueOf("targetFile");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        Caller caller = ctx.channel().attr(WebSocketAuthHandler_OTT.USER_ID_KEY).get();
        String userId = caller.getUid();

        // 1. 【起始帧】收到文件的第一个分片
        if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame startFrame = (BinaryWebSocketFrame) frame;

            // 初始化物理文件并开启追加模式 (Append Mode)
            File targetFile = new File("/data/uploads/huge_file_" + userId + ".dat");
            FileOutputStream fos = new FileOutputStream(targetFile, true);
            FileChannel fileChannel = fos.getChannel();

            // 暂存至上下文
            ctx.channel().attr(TARGET_FILE_KEY).set(targetFile);
            ctx.channel().attr(FILE_STREAM_KEY).set(fos);
            ctx.channel().attr(FILE_CHANNEL_KEY).set(fileChannel);

            // 写入并发送第一个 ACK
            long currentSize = writeAndGetNewSize(startFrame.content(), fileChannel);
            ctx.channel().attr(FILE_SIZE_KEY).set(currentSize);

            sendAck(ctx, currentSize, startFrame.isFinalFragment());

            if (startFrame.isFinalFragment()) {
                closeAndCleanUp(ctx, userId);
            }
        }

        // 2. 【连续帧】收到后续的无数个分片
        else if (frame instanceof ContinuationWebSocketFrame) {
            ContinuationWebSocketFrame continuationFrame = (ContinuationWebSocketFrame) frame;
            FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).get();

            if (fileChannel == null) {
                ctx.close(); // 异常协议流，直接截断
                return;
            }

            // 追加写入
            long currentSize = writeAndGetNewSize(continuationFrame.content(), fileChannel);
            ctx.channel().attr(FILE_SIZE_KEY).set(currentSize);

            // 响应实时进度 ACK 触发背压
            sendAck(ctx, currentSize, continuationFrame.isFinalFragment());

            // 检查当前帧是否是最后一块碎片
            if (continuationFrame.isFinalFragment()) {
                closeAndCleanUp(ctx, userId);
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
     * 关闭资源、执行落盘清理，并在最后触发生命周期收尾钩子
     */
    private void closeAndCleanUp(ChannelHandlerContext ctx, String userId) throws IOException {
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
        // 【核心触发点】此时文件已完整落盘，安全调用业务收尾方法
        // ==========================================
        if (targetFile != null && targetFile.exists()) {
            onUploadCompleted(ctx, targetFile);
        }

        System.out.println("用户 [" + userId + "] 的超大文件传输完成。");
    }

    /**
     * 大文件成功无损传输并落盘后的业务回调钩子（全功能升级版）
     */
    private void onUploadCompleted(ChannelHandlerContext ctx, File targetFile) {
        System.out.println("【系统】文件已落盘，启动 CompletableFuture 后台并行审计流水线...");
        ThreadPoolExecutor tpe = NioConfig.cfg.getBizExecutor();

        // 1. 任务 A：异步触发文件安全扫描（传递给 taskExecutor 线程池运行）
        CompletableFuture<Boolean> securityScanTask = CompletableFuture.supplyAsync(() -> {
            System.out.println("[线程-" + Thread.currentThread().getName() + "] 正在对文件执行杀毒与敏感合规扫描...");
            return executeVirusScan(targetFile); // 耗时操作
        }, tpe);

        // 2. 任务 B：异步触发媒体转码（并行在线程池的其他线程中运行）
        CompletableFuture<Boolean> videoTranscodeTask = CompletableFuture.supplyAsync(() -> {
            System.out.println("[线程-" + Thread.currentThread().getName() + "] 正在对大视频文件生成 HLS (.m3u8) 视频切片...");
            return executeVideoTranscode(targetFile); // 耗时操作
        }, tpe);

        // 3. 【核心联合点】将任务 A 和 任务 B 组合在一起
        // allOf 的意思是：后面所有的任务都完成了，这个大组合才算圆满结束
        CompletableFuture.allOf(securityScanTask, videoTranscodeTask)
                .thenAcceptAsync((voidResult) -> {
                    // 4. 当所有并行任务均在后台执行成功后，自动进入这个回调方法
                    try {
                        // 提取各个任务的执行结果 (.join() 在这里不会阻塞，因为 allOf 保证了它们已经执行完了)
                        boolean scanOk = securityScanTask.join();
                        boolean transcodeOk = videoTranscodeTask.join();

                        if (scanOk && transcodeOk) {
                            System.out.println("【成功】文件 [" + targetFile.getName() + "] 后台安全扫描与转码已全部顺利通过！");
                            // 通过 WebSocket 安全通知浏览器：整个业务流程全绿完工！
                            ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"status\":\"ALL_TASKS_COMPLETE\"}"));
                        } else {
                            // 某个环节业务审核失败
                            ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"status\":\"AUDIT_FAILED\",\"reason\":\"Security or Transcode failure\"}"));
                        }
                    } catch (Exception e) {
                        System.err.println("处理后台任务结果时发生异常: " + e.getMessage());
                    }
                }, ctx.executor())
                // 💡 避坑细节：使用 ctx.executor() 作为 thenAcceptAsync 的执行器，
                // 意思是让“发回 WebSocket 响应”的这个动作，重新回到 Netty 当前连接专属的 I/O 线程（EventLoop）中排队执行。
                // 这遵循了 Netty 的单线程无锁化设计原则，确保长连接写入的绝对线程安全！

                // 5. 统一异常拦截网：如果在上面的任何一个后台任务中发生了运行时崩溃（如 OOM 或 IOException），
                // 错误会被这里牢牢抓住，绝对不会发生普通 submit() 的“无意间隐式吞没异常”隐患！
                .exceptionally(throwable -> {
                    System.err.println("【严重错误】后台异步流水线遭遇致命崩溃: " + throwable.getMessage());
                    ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"status\":\"SERVER_ERROR\"}"));
                    return null;
                });
    }

    // ==========================================
    // 模拟耗时的底层耗时方法
    // ==========================================
    private boolean executeVirusScan(File file) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true; // 扫描通过
    }

    private boolean executeVideoTranscode(File file) {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true; // 转码通过
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        FileChannel fileChannel = ctx.channel().attr(FILE_CHANNEL_KEY).getAndSet(null);
        FileOutputStream fos = ctx.channel().attr(FILE_STREAM_KEY).getAndSet(null);

        if (fileChannel != null) fileChannel.close();
        if (fos != null) fos.close();

        ctx.close();
    }
}

