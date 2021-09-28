/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.nio.server;

import org.summerframework.security.SSLUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.logging.log4j.Level;
import org.summerframework.boot.instrumentation.NIOStatusListener;
import org.summerframework.boot.BootConstant;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class NioServer {

    private static final Logger log = LogManager.getLogger(NioServer.class.getName());

    private static EventLoopGroup bossGroup;// the pool to accept new connection requests
    private static EventLoopGroup workerGroup;// the pool to process IO logic
    //private static EventExecutorGroup sharedNioExecutorGroup;// a thread pool to handle time-consuming business
    private static final ScheduledExecutorService QPS_SERVICE = Executors.newSingleThreadScheduledExecutor();

    private static NIOStatusListener listener = null;

    public static void setStatusListener(NIOStatusListener l) {
        listener = l;
    }

    private static boolean serviceOk = true, servicePaused = false;
    private static HttpResponseStatus status = HttpResponseStatus.OK;
    private static String statusReason;

    public static void setServiceHealthOk(boolean newStatus, String reason) {
        boolean serviceStatusChanged = serviceOk != newStatus;
        serviceOk = newStatus;
        serviceStatusUpdate(serviceStatusChanged, reason);
    }

    public static void setServicePaused(boolean newStatus, String reason) {
        boolean serviceStatusChanged = servicePaused != newStatus;
        servicePaused = newStatus;
        serviceStatusUpdate(serviceStatusChanged, reason);
    }

    private static void serviceStatusUpdate(boolean serviceStatusChanged, String reason) {
        statusReason = reason;
        status = servicePaused
                ? HttpResponseStatus.SERVICE_UNAVAILABLE
                : (serviceOk ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE);
        if (serviceStatusChanged) {
            log.log(serviceOk ? Level.WARN : Level.FATAL, "\n\t server status changed: paused=" + servicePaused + ", OK=" + serviceOk + ", status=" + status + "\n\t reason: " + reason);
        }
    }

    public static boolean isServicePaused() {
        return servicePaused;
    }

    public static boolean isServiceStatusOk() {
        return serviceOk;
    }

    public static HttpResponseStatus getServiceStatus() {
        return status;
    }

    public static String getServiceStatusReason() {
        return statusReason;
    }

    /**
     *
     * @throws GeneralSecurityException
     * @throws IOException
     * @throws InterruptedException
     */
    public static void bind() throws GeneralSecurityException, IOException, InterruptedException {
        bind(NioConfig.CFG.getBindingAddresses());
    }

    /**
     *
     * @param bindingAddresses
     * @throws GeneralSecurityException
     * @throws IOException
     * @throws InterruptedException
     */
    public static void bind(Map<String, Integer> bindingAddresses) throws GeneralSecurityException, IOException, InterruptedException {
        if (bindingAddresses == null || bindingAddresses.isEmpty()) {
            log.info("Skip NIO server due to no bindingAddresses in config file: " + NioConfig.CFG.getCfgFile());
            return;
        }
        if (NioConfig.CFG.getRequestHandler() == null) {
            log.warn("Skip NIO server due to no RequestHandler in config file: " + NioConfig.CFG.getCfgFile());
            return;
        }

        IoMultiplexer multiplexer = NioConfig.CFG.getMultiplexer();
        log.info("starting... Epoll=" + Epoll.isAvailable() + ", KQueue=" + KQueue.isAvailable() + ", multiplexer=" + multiplexer);
        System.setProperty("io.netty.recycler.maxCapacity", "0");
        System.setProperty("io.netty.allocator.tinyCacheSize", "0");
        System.setProperty("io.netty.allocator.smallCacheSize", "0");
        System.setProperty("io.netty.allocator.normalCacheSize", "0");

        // Configure SSL.
        SSLContext jdkSslContext = null;
        SslContext nettySslContext = null;
        KeyManagerFactory kmf = NioConfig.CFG.getKmf();
        TrustManagerFactory tmf = NioConfig.CFG.getTmf();
        ClientAuth clientAuth = kmf != null && tmf != null ? ClientAuth.REQUIRE : ClientAuth.NONE;
        if (kmf != null) {
            List<String> ciphers;
            String[] cipherSuites = NioConfig.CFG.getSslCipherSuites();
            if (cipherSuites != null && cipherSuites.length > 0) {
                ciphers = Arrays.asList(NioConfig.CFG.getSslCipherSuites());
            } else {
                ciphers = Http2SecurityUtil.CIPHERS;
            }
            SslProvider sp = NioConfig.CFG.getSslProvider();
            if (sp == null) {
                jdkSslContext = SSLContext.getInstance(NioConfig.CFG.getSslProtocols()[0]);
                jdkSslContext.init(kmf.getKeyManagers(), tmf == null ? SSLUtil.TRUST_ALL_CERTIFICATES : tmf.getTrustManagers(), SecureRandom.getInstanceStrong());
            } else {
                nettySslContext = SslContextBuilder.forServer(kmf)
                        .trustManager(tmf)
                        .clientAuth(clientAuth)
                        .sslProvider(sp)
                        .sessionTimeout(0)
                        .protocols(NioConfig.CFG.getSslProtocols())
                        .ciphers(ciphers, SupportedCipherSuiteFilter.INSTANCE)
                        .build();
            }
            log.info(StringUtils.join("[" + sp + "] " + Arrays.asList(NioConfig.CFG.getSslProtocols())) + " (" + NioConfig.CFG.getSslHandshakeTimeout() + "s): " + ciphers);
        }

        // Configure the server.
        //boss and work groups
        int bossSize = NioConfig.CFG.getNioEventLoopGroupAcceptorSize();
        int workerSize = NioConfig.CFG.getNioEventLoopGroupWorkerSize();
        Class<? extends ServerChannel> serverChannelClass;
        if (Epoll.isAvailable() && (IoMultiplexer.AVAILABLE.equals(multiplexer) || IoMultiplexer.EPOLL.equals(multiplexer))) {
            bossGroup = bossSize < 1 ? new EpollEventLoopGroup() : new EpollEventLoopGroup(bossSize);
            workerGroup = workerSize < 1 ? new EpollEventLoopGroup() : new EpollEventLoopGroup(workerSize);
            serverChannelClass = EpollServerSocketChannel.class;
            multiplexer = IoMultiplexer.EPOLL;
        } else if (KQueue.isAvailable() && (IoMultiplexer.AVAILABLE.equals(multiplexer) || IoMultiplexer.KQUEUE.equals(multiplexer))) {
            bossGroup = bossSize < 1 ? new EpollEventLoopGroup() : new EpollEventLoopGroup(bossSize);
            workerGroup = workerSize < 1 ? new EpollEventLoopGroup() : new EpollEventLoopGroup(workerSize);
            serverChannelClass = KQueueServerSocketChannel.class;
            multiplexer = IoMultiplexer.KQUEUE;
        } else {
            bossGroup = bossSize < 1 ? new NioEventLoopGroup() : new NioEventLoopGroup(bossSize);
            workerGroup = workerSize < 1 ? new NioEventLoopGroup() : new NioEventLoopGroup(workerSize);
            serverChannelClass = NioServerSocketChannel.class;
            multiplexer = IoMultiplexer.JDK;
        }
        ServerBootstrap boot = new ServerBootstrap();
        if (multiplexer == IoMultiplexer.EPOLL) {
            boot.option(EpollChannelOption.SO_REUSEPORT, true);
        }
        boot.option(ChannelOption.SO_BACKLOG, NioConfig.CFG.getSoBacklog())
                .option(ChannelOption.SO_REUSEADDR, NioConfig.CFG.isSoReuseAddr())
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.SO_REUSEADDR, NioConfig.CFG.isSoReuseAddr())
                .childOption(ChannelOption.SO_KEEPALIVE, NioConfig.CFG.isSoKeepAlive())
                .childOption(ChannelOption.TCP_NODELAY, NioConfig.CFG.isSoTcpNodelay())
                .childOption(ChannelOption.SO_LINGER, NioConfig.CFG.getSoLinger())
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, NioConfig.CFG.getSoConnectionTimeout() * 1000)
                .childOption(ChannelOption.SO_RCVBUF, NioConfig.CFG.getSoRcvBuf())
                .childOption(ChannelOption.SO_SNDBUF, NioConfig.CFG.getSoSndBuf())
                //.childOption(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);// need to call ReferenceCountUtil.release(msg) after use. 使用内存池之后，内存的申请和释放必须成对出现，即retain()和release()要成对出现，否则会导致内存泄露。 值得注意的是，如果使用内存池，完成ByteBuf的解码工作之后必须显式的调用ReferenceCountUtil.release(msg)对接收缓冲区ByteBuf进行内存释放，否则它会被认为仍然在使用中，这样会导致内存泄露。

        boot.group(bossGroup, workerGroup)
                .channel(serverChannelClass)
                //.handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new NioServerHttpInitializer(jdkSslContext, nettySslContext, clientAuth.equals(ClientAuth.REQUIRE), NioConfig.CFG));

        for (String bindAddr : bindingAddresses.keySet()) {
            // info
            String sslMode;
            String protocol;
            if (jdkSslContext == null && nettySslContext == null) {
                sslMode = "non-ssl";
                protocol = multiplexer + " http://";
            } else {
                sslMode = "Client Auth: " + clientAuth;
                protocol = multiplexer + " https://";
            }
            int listeningPort = bindingAddresses.get(bindAddr);
            // bind
            ChannelFuture f = boot.bind(bindAddr, listeningPort).sync();
            f.channel().closeFuture().addListener((ChannelFutureListener) (ChannelFuture f1) -> {
                //shutdown();
                System.out.println("Server " + BootConstant.VERSION + " (" + sslMode + ") is stopped");
            });
            log.info(() -> "Server " + BootConstant.VERSION + " (" + sslMode + ") is listening on " + protocol + bindAddr + ":" + listeningPort + NioServerContext.getWebApiContextRoot());
            if (listener != null) {
                listener.onNIOBindNewPort(BootConstant.VERSION, sslMode, protocol, bindAddr, listeningPort, NioServerContext.getWebApiContextRoot());
            }
        }

        final long[] lastBizHit = {0, 0};
        if (listener != null || log.isDebugEnabled()) {
            int interval = 1;
            QPS_SERVICE.scheduleAtFixedRate(() -> {
                long hps = NioServerContext.COUNTER_HIT.getAndSet(0);
                long tps = NioServerContext.COUNTER_SENT.getAndSet(0);
                if (listener == null && !log.isDebugEnabled()) {
                    return;
                }
                long bizHit = NioServerContext.COUNTER_BIZ_HIT.get();
                if (lastBizHit[0] == bizHit && !servicePaused) {
                    return;
                }
                lastBizHit[0] = bizHit;
                ThreadPoolExecutor tpe = NioConfig.CFG.getBizExecutor();
                int active = tpe.getActiveCount();
                int queue = tpe.getQueue().size();
                if (hps > 0 || tps > 0 || active > 0 || queue > 0 || servicePaused) {
                    long totalChannel = NioServerContext.COUNTER_TOTAL_CHANNEL.get();
                    long activeChannel = NioServerContext.COUNTER_ACTIVE_CHANNEL.get();
                    long pool = tpe.getPoolSize();
                    int core = tpe.getCorePoolSize();
                    long max = tpe.getMaximumPoolSize();
                    long largest = tpe.getLargestPoolSize();
                    long task = tpe.getTaskCount();
                    long completed = tpe.getCompletedTaskCount();
                    long pingHit = NioServerContext.COUNTER_PING_HIT.get();
                    long totalHit = bizHit + pingHit;
                    log.debug(() -> "hps=" + hps + ", tps=" + tps + ", activeChannel=" + activeChannel + ", totalChannel=" + totalChannel + ", totalHit=" + totalHit + " (ping" + pingHit + " + biz" + bizHit + "), task=" + task + ", completed=" + completed + ", queue=" + queue + ", active=" + active + ", pool=" + pool + ", core=" + core + ", max=" + max + ", largest=" + largest);
                    if (listener != null) {
                        listener.onNIOAccessReportUpdate(hps, tps, totalHit, pingHit, bizHit, totalChannel, activeChannel, task, completed, queue, active, pool, core, max, largest);
                        //listener.onUpdate(data);//bad performance
                    }
                }
            }, 0, interval, TimeUnit.SECONDS);
        }
    }

    static void shutdown() {
        String tn = Thread.currentThread().getName();
        if (bossGroup != null && !bossGroup.isShutdown()) {
            System.out.println(tn + ": shutdown bossGroup");
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null && !workerGroup.isShutdown()) {
            System.out.println(tn + ": shutdown workerGroup");
            workerGroup.shutdownGracefully();
        }

//        EventExecutorGroup childExecutor = CFG.getNioSharedChildExecutor();
//        if (childExecutor != null) {
//            childExecutor.shutdownGracefully();
//        }
        if (!QPS_SERVICE.isShutdown()) {
            System.out.println(tn + ": shutdown QPS_SERVICE");
            QPS_SERVICE.shutdownNow();
        }
    }

}
