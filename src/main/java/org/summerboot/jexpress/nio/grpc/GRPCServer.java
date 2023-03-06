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
package org.summerboot.jexpress.nio.grpc;

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;
import org.summerboot.jexpress.nio.server.AbortPolicyWithReport;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GRPCServer {

    protected static final Logger log = LogManager.getLogger(GRPCServer.class.getName());

    public static ServerCredentials initTLS(KeyManagerFactory kmf, TrustManagerFactory tmf) {
        if (kmf == null) {
            return null;
        }
        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder().keyManager(kmf.getKeyManagers());
        if (tmf != null) {
            tlsBuilder.trustManager(tmf.getTrustManagers());
            tlsBuilder.clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);
        }
        return tlsBuilder.build();
    }

    protected Server server = null;
    protected final String bindingAddr;
    protected final int port;
    protected final ServerCredentials serverCredentials;
    protected final ServerBuilder serverBuilder;

    protected ScheduledExecutorService statusReporter = null;
    protected ThreadPoolExecutor tpe = null;
    protected static NIOStatusListener listener = null;
    protected boolean servicePaused = false;
    protected final Counter counter = new Counter();

    public GRPCServer(String bindingAddr, int port, KeyManagerFactory kmf, TrustManagerFactory tmf) {
        this(bindingAddr, port, initTLS(kmf, tmf));
    }

    public GRPCServer(String bindingAddr, int port, ServerCredentials serverCredentials) {
        this.bindingAddr = bindingAddr;
        this.port = port;
        this.serverCredentials = serverCredentials;
        if (serverCredentials == null) {
            serverBuilder = NettyServerBuilder.forAddress(new InetSocketAddress(bindingAddr, port));
        } else {
            serverBuilder = Grpc.newServerBuilderForPort(port, serverCredentials);
        }
        //serverBuilder.executor(tpe)
        //AbstractImplBase implBase = 
        //serverBuilder.addService(implBase);
    }

    public static void setListener(NIOStatusListener listener) {
        GRPCServer.listener = listener;
    }

    public Counter configThreadPool() {
        final int coreSize = Runtime.getRuntime().availableProcessors();
        int poolCoreSize = coreSize + 1;// how many tasks running at the same time
        int poolMaxSizeMaxSize = poolCoreSize;// how many tasks running at the same time
        long keepAliveSeconds = 60L;
        int poolQueueSize = Integer.MAX_VALUE;// waiting list size when the pool is full
        return this.configThreadPool(poolCoreSize, poolMaxSizeMaxSize, poolQueueSize, keepAliveSeconds);
    }

    /**
     *
     * @param poolCoreSize - the number of threads to keep in the pool, even if
     * they are idle, unless allowCoreThreadTimeOutis set
     * @param poolMaxSizeMaxSize - the maximum number of threads to allow in the
     * pool
     * @param poolQueueSize - the size of the waiting list
     * @param keepAliveSeconds - when the number of threads is greater than the
     * core, this is the maximum time that excess idle threads will wait for new
     * tasks before terminating.
     * @return
     */
    public Counter configThreadPool(int poolCoreSize, int poolMaxSizeMaxSize, int poolQueueSize, long keepAliveSeconds) {
        ThreadPoolExecutor old = tpe;
        tpe = new ThreadPoolExecutor(poolCoreSize, poolMaxSizeMaxSize, keepAliveSeconds, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(poolQueueSize),
                Executors.defaultThreadFactory(), new AbortPolicyWithReport("gRPC Server Executor"));//.DiscardOldestPolicy()
        serverBuilder.executor(tpe);
        if (old != null) {
            old.shutdown();
        }

        int interval = 1;
        final AtomicReference<Long> lastBizHitRef = new AtomicReference<>();
        lastBizHitRef.set(-1L);
        long totalChannel = -1;//NioServerContext.COUNTER_TOTAL_CHANNEL.get();
        long activeChannel = -1;//NioServerContext.COUNTER_ACTIVE_CHANNEL.get();
        ScheduledExecutorService old2 = statusReporter;
        statusReporter = Executors.newSingleThreadScheduledExecutor();
        statusReporter.scheduleAtFixedRate(() -> {
            if (listener == null && !log.isDebugEnabled()) {
                return;
            }
            long bizHit = counter.getBiz();
            //if (lastBizHit[0] == bizHit && !servicePaused) {
            if (lastBizHitRef.get() == bizHit && !servicePaused) {
                return;
            }
            lastBizHitRef.set(bizHit);
            long hps = counter.getHitAndReset();
            long tps = counter.getProcessedAndReset();
            long pingHit = counter.getPing();
            long totalHit = bizHit + pingHit;

            int active = tpe.getActiveCount();
            int queue = tpe.getQueue().size();
            if (hps > 0 || tps > 0 || active > 0 || queue > 0 || servicePaused) {
//                long totalChannel = NioServerContext.COUNTER_TOTAL_CHANNEL.get();
//                long activeChannel = NioServerContext.COUNTER_ACTIVE_CHANNEL.get();
                long pool = tpe.getPoolSize();
                int core = tpe.getCorePoolSize();
                //int queueRemainingCapacity = tpe.getQueue().remainingCapacity();
                long max = tpe.getMaximumPoolSize();
                long largest = tpe.getLargestPoolSize();
                long task = tpe.getTaskCount();
                long completed = tpe.getCompletedTaskCount();
                log.debug(() -> "hps=" + hps + ", tps=" + tps + ", totalHit=" + totalHit + " (ping " + pingHit + " + biz " + bizHit + "), queue=" + queue + ", active=" + active + ", pool=" + pool + ", core=" + core + ", max=" + max + ", largest=" + largest + ", task=" + task + ", completed=" + completed + ", activeChannel=" + activeChannel + ", totalChannel=" + totalChannel);
                if (listener != null) {
                    listener.onNIOAccessReportUpdate(hps, tps, totalHit, pingHit, bizHit, totalChannel, activeChannel, task, completed, queue, active, pool, core, max, largest);
                    //listener.onUpdate(data);//bad performance
                }
            }
        }, 0, interval, TimeUnit.SECONDS);
        if (old2 != null) {
            old2.shutdownNow();
        }
        return counter;
    }

    public ServerBuilder serverBuilder() {
        return serverBuilder;
    }

    public void start() throws IOException {
        start(false);
    }

    public void start(boolean isBlock) throws IOException {
        if (server != null) {
            shutdown();
        }

        server = serverBuilder.build().start();
        String schema = serverCredentials == null ? "grpc" : "grpcs";
        log.info("*** GRPCServer is listening on " + schema + "://" + bindingAddr + ":" + port);
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    shutdown();
                }, "GRPCServer.shutdown and stop listening on " + schema + "://" + bindingAddr + ":" + port));
        if (isBlock) {
            try {
                server.awaitTermination();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() {
        if (server == null) {
            return;
        }
        try {
            server.shutdown();
            if (statusReporter != null) {
                statusReporter.shutdown();
            }
            log.warn("*** GRPCServer shutdown " + bindingAddr + ":" + port);
            server.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("GRPCServer shutdown timeout " + bindingAddr + ":" + port);
        } finally {
            server = null;
        }
    }
}
