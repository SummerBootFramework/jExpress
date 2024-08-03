/*
 * Copyright 2005-2022 Du Law Office - The Summer Boot Framework Project
 *
 * The Summer Boot Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License and you have no
 * policy prohibiting employee contributions back to this file (unless the contributor to this
 * file is your current or retired employee). You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.summerboot.jexpress.nio.server;

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
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.config.NamedDefaultThreadFactory;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class NioServer {

    protected static final Logger log = LogManager.getLogger(NioServer.class.getName());

    protected EventLoopGroup bossGroup;// the pool to accept new connection requests
    protected EventLoopGroup workerGroup;// the pool to process IO logic
    //protected  EventExecutorGroup sharedNioExecutorGroup;// a thread pool to handle time-consuming business
    protected ScheduledExecutorService QPS_SERVICE;// = Executors.newSingleThreadScheduledExecutor();

    protected final NioChannelInitializer channelInitializer;
    protected final NIOStatusListener nioListener;

    public NioServer(NioChannelInitializer channelInitializer, NIOStatusListener nioListener) {
        this.channelInitializer = channelInitializer;
        this.nioListener = nioListener;
    }

    /**
     * @param nioCfg
     * @throws InterruptedException
     * @throws SSLException
     */
    public void bind(NioConfig nioCfg) throws InterruptedException, SSLException {
        List<InetSocketAddress> bindingAddresses = nioCfg.getBindingAddresses();
        if (bindingAddresses == null || bindingAddresses.isEmpty()) {
            log.info("Skip HTTP server due to no bindingAddresses in config file: " + nioCfg.getCfgFile());
            return;
        }
//        if (nioCfg.getRequestHandler() == null) {
//            log.warn("Skip HTTP server due to no RequestHandler in config file: " + nioCfg.getCfgFile());
//            return;
//        }

        IoMultiplexer multiplexer = nioCfg.getMultiplexer();
        log.info("starting... Epoll=" + Epoll.isAvailable() + ", KQueue=" + KQueue.isAvailable() + ", multiplexer=" + multiplexer);
        System.setProperty("io.netty.recycler.maxCapacity", "0");
        System.setProperty("io.netty.allocator.tinyCacheSize", "0");
        System.setProperty("io.netty.allocator.smallCacheSize", "0");
        System.setProperty("io.netty.allocator.normalCacheSize", "0");

        // Configure SSL.
        SSLContext jdkSslContext = null;
        SslContext nettySslContext = null;
        KeyManagerFactory kmf = nioCfg.getKmf();
        TrustManagerFactory tmf = nioCfg.getTmf();
        ClientAuth clientAuth = kmf != null && tmf != null ? ClientAuth.REQUIRE : ClientAuth.NONE;
        if (kmf != null) {
            List<String> ciphers;
            String[] cipherSuites = nioCfg.getSslCipherSuites();
            if (cipherSuites != null && cipherSuites.length > 0) {
                ciphers = Arrays.asList(nioCfg.getSslCipherSuites());
            } else {
                ciphers = Http2SecurityUtil.CIPHERS;
            }
            SslProvider sp = nioCfg.getSslProvider();
//            if (sp == null) {
//                jdkSslContext = SSLContext.getInstance(instance.getSslProtocols()[0]);
//                jdkSslContext.init(kmf.getKeyManagers(), tmf == null ? SSLUtil.TRUST_ALL_CERTIFICATES : tmf.getTrustManagers(), SecureRandom.getInstanceStrong());
//            } else {
            nettySslContext = SslContextBuilder.forServer(kmf)
                    .trustManager(tmf)
                    .clientAuth(clientAuth)
                    .sslProvider(sp)
                    .sessionTimeout(0)
                    .protocols(nioCfg.getSslProtocols())
                    .ciphers(ciphers, SupportedCipherSuiteFilter.INSTANCE)
                    .build();
//            }
            log.info(StringUtils.join("[" + sp + "] " + Arrays.asList(nioCfg.getSslProtocols())) + " (" + nioCfg.getSslHandshakeTimeoutSeconds() + "s): " + ciphers);
        }

        // Configure the server.
        //boss and work groups
        int bossSize = nioCfg.getNioEventLoopGroupAcceptorSize();
        int workerSize = nioCfg.getNioEventLoopGroupWorkerSize();
        Class<? extends ServerChannel> serverChannelClass;
        ThreadFactory threadFactoryBoss = new NamedDefaultThreadFactory("NIO.Boss");
        ThreadFactory threadFactoryWorker = new NamedDefaultThreadFactory("NIO.Worker");
        if (Epoll.isAvailable() && (IoMultiplexer.AVAILABLE.equals(multiplexer) || IoMultiplexer.EPOLL.equals(multiplexer))) {
            bossGroup = bossSize < 1 ? new EpollEventLoopGroup() : new EpollEventLoopGroup(bossSize, threadFactoryBoss);
            workerGroup = workerSize < 1 ? new EpollEventLoopGroup() : new EpollEventLoopGroup(workerSize, threadFactoryWorker);
            serverChannelClass = EpollServerSocketChannel.class;
            multiplexer = IoMultiplexer.EPOLL;
        } else if (KQueue.isAvailable() && (IoMultiplexer.AVAILABLE.equals(multiplexer) || IoMultiplexer.KQUEUE.equals(multiplexer))) {
            bossGroup = bossSize < 1 ? new EpollEventLoopGroup() : new EpollEventLoopGroup(bossSize, threadFactoryBoss);
            workerGroup = workerSize < 1 ? new EpollEventLoopGroup() : new EpollEventLoopGroup(workerSize, threadFactoryWorker);
            serverChannelClass = KQueueServerSocketChannel.class;
            multiplexer = IoMultiplexer.KQUEUE;
        } else {
            bossGroup = bossSize < 1 ? new NioEventLoopGroup() : new NioEventLoopGroup(bossSize, threadFactoryBoss);
            workerGroup = workerSize < 1 ? new NioEventLoopGroup() : new NioEventLoopGroup(workerSize, threadFactoryWorker);
            serverChannelClass = NioServerSocketChannel.class;
            multiplexer = IoMultiplexer.JDK;
        }
        ServerBootstrap boot = new ServerBootstrap();
        if (multiplexer == IoMultiplexer.EPOLL) {
            boot.option(EpollChannelOption.SO_REUSEPORT, true);
        }
        boot.option(ChannelOption.SO_BACKLOG, nioCfg.getSoBacklog())
                .option(ChannelOption.SO_REUSEADDR, nioCfg.isSoReuseAddr())
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.SO_REUSEADDR, nioCfg.isSoReuseAddr())
                .childOption(ChannelOption.SO_KEEPALIVE, nioCfg.isSoKeepAlive())
                .childOption(ChannelOption.TCP_NODELAY, nioCfg.isSoTcpNodelay())
                .childOption(ChannelOption.SO_LINGER, nioCfg.getSoLinger())
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, nioCfg.getSoConnectionTimeoutSeconds() * 1000)
                .childOption(ChannelOption.SO_RCVBUF, nioCfg.getSoRcvBuf())
                .childOption(ChannelOption.SO_SNDBUF, nioCfg.getSoSndBuf())
                //.childOption(ChannelOption.SINGLE_EVENTEXECUTOR_PER_GROUP, false)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);// need to call ReferenceCountUtil.release(msg) after use. 使用内存池之后，内存的申请和释放必须成对出现，即retain()和release()要成对出现，否则会导致内存泄露。 值得注意的是，如果使用内存池，完成ByteBuf的解码工作之后必须显式的调用ReferenceCountUtil.release(msg)对接收缓冲区ByteBuf进行内存释放，否则它会被认为仍然在使用中，这样会导致内存泄露。

        channelInitializer.initSSL(nettySslContext, nioCfg);
        boot.group(bossGroup, workerGroup)
                .channel(serverChannelClass)
                //.handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(channelInitializer);

        String appInfo = BootConstant.VERSION + " " + BootConstant.PID;
        List<String> loadBalancingEndpoints = BackOffice.agent.getLoadBalancingPingEndpoints();
        //for (String bindAddr : bindingAddresses.keySet()) {
        for (InetSocketAddress addr : bindingAddresses) {
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
            String bindAddr = addr.getAddress().getHostAddress();
            int listeningPort = addr.getPort();
            // bind
            ChannelFuture f = boot.bind(bindAddr, listeningPort).sync();
            f.channel().closeFuture().addListener((ChannelFutureListener) (ChannelFuture f1) -> {
                //shutdown();
                System.out.println("Server " + appInfo + " (" + sslMode + ") is stopped");
            });
            List<String> loadBalancingPingEndpoints = BackOffice.agent.getLoadBalancingPingEndpoints();
            for (String loadBalancingPingEndpoint : loadBalancingPingEndpoints) {
                log.info(() -> "Server " + appInfo + " (" + sslMode + ") is listening on " + protocol + bindAddr + ":" + listeningPort + (loadBalancingPingEndpoint == null ? "" : loadBalancingPingEndpoint));
            }

            if (nioListener != null) {
                nioListener.onNIOBindNewPort(appInfo, sslMode, protocol, bindAddr, listeningPort, loadBalancingEndpoints);
            }
        }

        //final long[] lastBizHit = {0, 0};
        final AtomicReference<Long> lastBizHitRef = new AtomicReference<>();
        lastBizHitRef.set(-1L);
        if (nioListener != null || log.isDebugEnabled()) {
            int interval = 1;
            QPS_SERVICE = Executors.newSingleThreadScheduledExecutor(new NamedDefaultThreadFactory("NIO.QPS_SERVICE"));
            QPS_SERVICE.scheduleAtFixedRate(() -> {
                long hps = NioCounter.COUNTER_HIT.getAndSet(0);
                long tps = NioCounter.COUNTER_SENT.getAndSet(0);
                if (nioListener == null && !log.isDebugEnabled()) {
                    return;
                }
                long bizHit = NioCounter.COUNTER_BIZ_HIT.get();
                //if (lastBizHit[0] == bizHit && !servicePaused) {
                if (lastBizHitRef.get() == bizHit && !HealthMonitor.isServicePaused()) {
                    return;
                }
                //lastBizHit[0] = bizHit;
                lastBizHitRef.set(bizHit);
                ThreadPoolExecutor tpe = nioCfg.getBizExecutor();
                int active = tpe.getActiveCount();
                int queue = tpe.getQueue().size();
                long activeChannel = NioCounter.COUNTER_ACTIVE_CHANNEL.get();
                if (hps > 0 || tps > 0 || active > 0 || queue > 0 || activeChannel > 0) {
                    long totalChannel = NioCounter.COUNTER_TOTAL_CHANNEL.get();
                    long pool = tpe.getPoolSize();
                    int core = tpe.getCorePoolSize();
                    //int queueRemainingCapacity = tpe.getQueue().remainingCapacity();
                    long max = tpe.getMaximumPoolSize();
                    long largest = tpe.getLargestPoolSize();
                    long task = tpe.getTaskCount();
                    long completed = tpe.getCompletedTaskCount();
                    long pingHit = NioCounter.COUNTER_PING_HIT.get();
                    long totalHit = bizHit + pingHit;
                    log.debug(() -> "hps=" + hps + ", tps=" + tps + ", activeChannel=" + activeChannel + ", totalChannel=" + totalChannel + ", totalHit=" + totalHit + " (ping" + pingHit + " + biz" + bizHit + "), task=" + task + ", completed=" + completed + ", queue=" + queue + ", active=" + active + ", pool=" + pool + ", core=" + core + ", max=" + max + ", largest=" + largest);
                    if (nioListener != null) {
                        nioListener.onNIOAccessReportUpdate(appInfo, hps, tps, totalHit, pingHit, bizHit, totalChannel, activeChannel, task, completed, queue, active, pool, core, max, largest);
                        //listener.onUpdate(data);//bad performance
                    }
                }
            }, 0, interval, TimeUnit.SECONDS);
        }
    }

    public void shutdown() {
        String tn = Thread.currentThread().getName();
        if (bossGroup != null && !bossGroup.isShutdown()) {
            System.out.println(tn + ": shutdown bossGroup");
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null && !workerGroup.isShutdown()) {
            System.out.println(tn + ": shutdown workerGroup");
            workerGroup.shutdownGracefully();
        }

//        EventExecutorGroup childExecutor = instance.getNioSharedChildExecutor();
//        if (childExecutor != null) {
//            childExecutor.shutdownGracefully();
//        }
        if (QPS_SERVICE != null && !QPS_SERVICE.isShutdown()) {
            System.out.println(tn + ": shutdown QPS_SERVICE");
            QPS_SERVICE.shutdownNow();
        }
    }

}
