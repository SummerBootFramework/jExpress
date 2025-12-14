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
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
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
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.NamedDefaultThreadFactory;
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;
import org.summerboot.jexpress.nio.IdleEventMonitor;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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

    public static final IdleEventMonitor IDLE_EVENT_MONITOR = new IdleEventMonitor(NioServer.class.getSimpleName()) {
        @Override
        public long getIdleIntervalMillis() {
            return TimeUnit.SECONDS.toMillis(NioConfig.cfg.getIdleThresholdSecond());
        }
    };

    public NioServer(NioChannelInitializer channelInitializer, NIOStatusListener nioListener) {
        this.channelInitializer = channelInitializer;
        this.nioListener = nioListener;
    }

    /**
     * @param nioCfg
     * @throws InterruptedException
     * @throws SSLException
     */
    public void bind(NioConfig nioCfg, StringBuilder memo) throws InterruptedException, SSLException {
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
        boolean isTLSEnabled = nioCfg.isTLSEnabled();
        ClientAuth clientAuth = isTLSEnabled && tmf != null ? ClientAuth.REQUIRE : ClientAuth.NONE;
        if (isTLSEnabled) {
            if (kmf == null) {
                throw new IllegalStateException("NioConfig with TLS is enabled by assigning TLS protocols, but " + NioConfig.KEY_kmf_key + " for TLS/SSL configuration is not properly configured");
            }
            List<String> ciphers;
            String[] cipherSuites = nioCfg.getTlsCipherSuites();
            if (cipherSuites != null && cipherSuites.length > 0) {
                ciphers = Arrays.asList(cipherSuites);
            } else {
                ciphers = Http2SecurityUtil.CIPHERS;
            }
            SslProvider sp = nioCfg.getSslProvider();
//            if (sp == null) {
//                jdkSslContext = SSLContext.getInstance(instance.getSslProtocols()[0]);
//                jdkSslContext.init(kmf.getKeyManagers(), tmf == null ? SSLUtil.TRUST_ALL_CERTIFICATES : tmf.getTrustManagers(), SecureRandom.getInstanceStrong());
//            } else {
            String[] tlsProtocols = nioCfg.getTlsProtocols();
            nettySslContext = SslContextBuilder.forServer(kmf)
                    .trustManager(tmf)
                    .clientAuth(clientAuth)
                    .sslProvider(sp)
                    .sessionTimeout(0)
                    .protocols(tlsProtocols)
                    .ciphers(ciphers, SupportedCipherSuiteFilter.INSTANCE)
                    .build();
//            }
            log.info(StringUtils.join("[" + sp + "] " + Arrays.asList(tlsProtocols)) + " (" + nioCfg.getSslHandshakeTimeoutSeconds() + "s): " + ciphers);
        }

        // Configure the server.
        //boss and work groups

        int bossSize = Math.max(0, nioCfg.getNioEventLoopGroupAcceptorSize());
        int workerSize = Math.max(0, nioCfg.getNioEventLoopGroupWorkerSize());

        Class<? extends ServerChannel> serverChannelClass;
        ThreadFactory threadFactoryBoss = NamedDefaultThreadFactory.build("Netty-HTTP.Boss", nioCfg.isNioEventLoopGroupAcceptorUseVirtualThread());
        ThreadFactory threadFactoryWorker = NamedDefaultThreadFactory.build("Netty-HTTP.Worker", nioCfg.isNioEventLoopGroupWorkerUseVirtualThread());

        if (Epoll.isAvailable() && (IoMultiplexer.AVAILABLE.equals(multiplexer) || IoMultiplexer.EPOLL.equals(multiplexer))) {
            //bossGroup = new EpollEventLoopGroup(bossSize, threadFactoryBoss);
            //workerGroup = new EpollEventLoopGroup(workerSize, threadFactoryWorker);
            bossGroup = new MultiThreadIoEventLoopGroup(bossSize, threadFactoryBoss, EpollIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(workerSize, threadFactoryWorker, EpollIoHandler.newFactory());
            serverChannelClass = EpollServerSocketChannel.class;
            multiplexer = IoMultiplexer.EPOLL;
        } else if (KQueue.isAvailable() && (IoMultiplexer.AVAILABLE.equals(multiplexer) || IoMultiplexer.KQUEUE.equals(multiplexer))) {
            //bossGroup = new EpollEventLoopGroup(bossSize, threadFactoryBoss);
            //workerGroup = new EpollEventLoopGroup(workerSize, threadFactoryWorker);
            bossGroup = new MultiThreadIoEventLoopGroup(bossSize, threadFactoryBoss, EpollIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(workerSize, threadFactoryWorker, EpollIoHandler.newFactory());
            serverChannelClass = KQueueServerSocketChannel.class;
            multiplexer = IoMultiplexer.KQUEUE;
        } else {
            //bossGroup = new NioEventLoopGroup(bossSize, threadFactoryBoss);
            //workerGroup = new NioEventLoopGroup(workerSize, threadFactoryWorker);
            bossGroup = new MultiThreadIoEventLoopGroup(bossSize, threadFactoryBoss, NioIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(workerSize, threadFactoryWorker, NioIoHandler.newFactory());
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
        Set<String> loadBalancingPingEndpoints = BackOffice.agent.getLoadBalancingPingEndpoints();
        if (loadBalancingPingEndpoints.isEmpty()) {
            loadBalancingPingEndpoints.add("");
        }
        //for (String bindAddr : bindingAddresses.keySet()) {
        for (InetSocketAddress addr : bindingAddresses) {
            // info
            String sslMode;
            String protocol;
            if (jdkSslContext == null && nettySslContext == null) {
                sslMode = "non-ssl";
                protocol = "http://";
            } else {
                sslMode = "Client Auth: " + clientAuth;
                protocol = "https://";
            }
            String listenerInfo = "[multiplexer=" + multiplexer + "] " + sslMode;
            String bindAddr = addr.getAddress().getHostAddress();
            int listeningPort = addr.getPort();
// bind
            ChannelFuture f = boot.bind(bindAddr, listeningPort).sync();
            f.channel().closeFuture().addListener((ChannelFutureListener) (ChannelFuture f1) -> {
                //shutdown();
                System.out.println("Server " + appInfo + " (" + listenerInfo + ") is stopped");
            });

            for (String loadBalancingPingEndpoint : loadBalancingPingEndpoints) {
                String info = "Netty HTTP server [" + appInfo + "] (" + listenerInfo + ") is listening on " + protocol + bindAddr + ":" + listeningPort + (loadBalancingPingEndpoint == null ? "" : loadBalancingPingEndpoint);
                memo.append(BootConstant.BR).append(info);
                log.info(() -> info);
            }

            if (nioListener != null) {
                nioListener.onNIOBindNewPort(appInfo, listenerInfo, protocol, bindAddr, listeningPort, loadBalancingPingEndpoints);
            }
        }

        //final long[] lastBizHit = {0, 0};
        final AtomicReference<Long> lastBizHitRef = new AtomicReference<>();
        lastBizHitRef.set(-1L);
        if (nioListener != null || log.isDebugEnabled()) {
            final AtomicLong lastChecksum = new AtomicLong(0);
            int interval = 1;
            boolean useVirtualThread = nioCfg.getTpeThreadingMode().equals(BootConfig.ThreadingMode.VirtualThread);
            QPS_SERVICE = Executors.newSingleThreadScheduledExecutor(NamedDefaultThreadFactory.build("NIO.QPS_SERVICE", useVirtualThread));
            QPS_SERVICE.scheduleAtFixedRate(() -> {
                long hps = NioCounter.COUNTER_HIT.getAndSet(0);
                long tps = NioCounter.COUNTER_SENT.getAndSet(0);
                if (nioListener == null && !log.isDebugEnabled()) {
                    return;
                }
                long bizHit = NioCounter.COUNTER_BIZ_HIT.get();
                //if (lastBizHit[0] == bizHit && !servicePaused) {
//                if (lastBizHitRef.get() == bizHit && !HealthMonitor.isServicePaused()) {
//                    return;
//                }
                //lastBizHit[0] = bizHit;
                lastBizHitRef.set(bizHit);
                ThreadPoolExecutor tpe = nioCfg.getBizExecutor();
                int active = tpe.getActiveCount();
                int queue = tpe.getQueue().size();
                long activeChannel = NioCounter.COUNTER_ACTIVE_CHANNEL.get();
                //if (hps > 0 || tps > 0 || active > 0 || queue > 0 || activeChannel > 0) {
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
                long checksum = hps + tps + bizHit + task + completed + queue + active + core + max + largest;
                if (log.isTraceEnabled()) {
                    checksum += pool;
                }
                if (lastChecksum.get() != checksum) {
                    lastChecksum.set(checksum);
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
