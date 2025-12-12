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

import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.ServerInterceptor;
import io.grpc.TlsServerCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.config.NamedDefaultThreadFactory;
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;
import org.summerboot.jexpress.nio.IdleEventMonitor;
import org.summerboot.jexpress.nio.server.SessionContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GRPCServer {

    protected static final Logger log = LogManager.getLogger(GRPCServer.class.getName());

    protected static final GRPCServiceCounter serviceCounter = new GRPCServiceCounter();

    public static Context.Key<SessionContext> SessionContext = Context.key("SessionContext");

    protected final String bindingAddr;
    protected final int port;
    protected final ServerCredentials serverCredentials;

    protected final ServerBuilder serverBuilder;

    protected Server server = null;

    protected ScheduledExecutorService statusReporter = null;
    //protected boolean servicePaused = false;

    public static final IdleEventMonitor IDLE_EVENT_MONITOR = new IdleEventMonitor(GRPCServer.class.getSimpleName());

    public ServerBuilder getServerBuilder() {
        return serverBuilder;
    }

    static GRPCServiceCounter getServiceCounter() {
        return serviceCounter;
    }

    public GRPCServer(String bindingAddr, int port, KeyManagerFactory kmf, TrustManagerFactory tmf, ThreadPoolExecutor tpe, boolean useVirtualThread, boolean generateReport, NIOStatusListener nioListener, ServerInterceptor... serverInterceptors) {
        this.bindingAddr = bindingAddr;
        this.port = port;
        serverCredentials = initTLS(kmf, tmf);
        if (serverCredentials == null) {
            serverBuilder = NettyServerBuilder.forAddress(new InetSocketAddress(bindingAddr, port));
        } else {
            serverBuilder = Grpc.newServerBuilderForPort(port, serverCredentials);
        }
        if (serverInterceptors != null) {
            for (ServerInterceptor serverInterceptor : serverInterceptors) {
                serverBuilder.intercept(serverInterceptor);
            }
        }
        serverBuilder.executor(tpe);
        if (generateReport) {
            report(tpe, useVirtualThread, nioListener, bindingAddr, port);
        }
    }

    protected ServerCredentials initTLS(KeyManagerFactory kmf, TrustManagerFactory tmf) {
        if (kmf == null) {
            return null;
        }
        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder().keyManager(kmf.getKeyManagers());
        if (tmf != null) {
            tlsBuilder.trustManager(tmf.getTrustManagers());
            tlsBuilder.clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);
        } else {
            tlsBuilder.clientAuth(TlsServerCredentials.ClientAuth.NONE);
        }
        return tlsBuilder.build();
    }

    /**
     * @param tpe
     * @param useVirtualThread
     * @param nioListener
     * @param bindingAddr
     * @param port
     */
    protected void report(ThreadPoolExecutor tpe, boolean useVirtualThread, NIOStatusListener nioListener, String bindingAddr, int port) {
        int interval = 1;
        final AtomicReference<Long> lastBizHitRef = new AtomicReference<>();
        lastBizHitRef.set(-1L);
        long totalChannel = -1;//NioServerContext.COUNTER_TOTAL_CHANNEL.get();
        long activeChannel = -1;//NioServerContext.COUNTER_ACTIVE_CHANNEL.get();
        ScheduledExecutorService old2 = statusReporter;
        statusReporter = Executors.newSingleThreadScheduledExecutor(NamedDefaultThreadFactory.build("Netty-gRPC.QPS_SERVICE@" + bindingAddr + ":" + port, useVirtualThread));
        final AtomicLong lastChecksum = new AtomicLong(0);
        String appInfo = "gRPC@" + BootConstant.VERSION + " " + BootConstant.PID;
        statusReporter.scheduleAtFixedRate(() -> {
            if (nioListener == null && !log.isDebugEnabled()) {
                return;
            }
            long bizHit = serviceCounter.getBiz();
            long cancelled = serviceCounter.getCancelled();
            lastBizHitRef.set(bizHit);
            long hps = serviceCounter.getHitAndReset();
            long tps = serviceCounter.getProcessedAndReset();
            long pingHit = serviceCounter.getPing();
            long totalHit = bizHit + pingHit;

            int active = tpe.getActiveCount();
            int queue = tpe.getQueue().size();
            //if (hps > 0 || tps > 0 || active > 0 || queue > 0 || servicePaused) {
//                long totalChannel = NioServerContext.COUNTER_TOTAL_CHANNEL.get();
//                long activeChannel = NioServerContext.COUNTER_ACTIVE_CHANNEL.get();
            long pool = tpe.getPoolSize();
            int core = tpe.getCorePoolSize();
            //int queueRemainingCapacity = tpe.getQueue().remainingCapacity();
            long max = tpe.getMaximumPoolSize();
            long largest = tpe.getLargestPoolSize();
            long task = tpe.getTaskCount();
            long completed = tpe.getCompletedTaskCount();
            long checksum = hps + tps + bizHit + /*task + completed*/ +queue + active + core + max /*+ largest*/;
            if (log.isTraceEnabled()) {
                checksum += pool;
            }
            if (lastChecksum.get() != checksum) {
                lastChecksum.set(checksum);
                log.debug(() -> "hps=" + hps + ", tps=" + tps + ", totalHit=" + totalHit + " (ping" + pingHit + " + biz" + bizHit + " + cancelled" + cancelled + "), task=" + task + ", completed=" + completed + ", queue=" + queue + ", active=" + active + ", pool=" + pool + ", core=" + core + ", max=" + max + ", largest=" + largest);
                if (nioListener != null) {
                    nioListener.onNIOAccessReportUpdate(appInfo, hps, tps, totalHit, pingHit, bizHit, totalChannel, activeChannel, task, completed, queue, active, pool, core, max, largest);
                    //listener.onUpdate(data);//bad performance
                }
            }
            //}
        }, 0, interval, TimeUnit.SECONDS);
        if (old2 != null) {
            old2.shutdownNow();
        }
    }

    public void start(StringBuilder memo) throws IOException {
        this.start(false, memo);
    }

    /**
     * openssl s_client -connect server:port -alpn h2
     *
     * @param isBlockingMode
     * @throws IOException
     */
    public void start(boolean isBlockingMode, StringBuilder memo) throws IOException {
        if (server != null) {
            shutdown();
        }
        String appInfo = BootConstant.VERSION + " " + BootConstant.PID;
        server = serverBuilder.build().start();
        String schema = serverCredentials == null ? "grpc" : "grpcs";
        String info = "Netty GRPC server [" + appInfo + "] is listening on " + schema + "://" + bindingAddr + ":" + port;
        memo.append(BootConstant.BR).append(info);
        log.info(info);
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    shutdown();
                }, "GRPCServer.shutdown and stop listening on " + schema + "://" + bindingAddr + ":" + port));
        if (isBlockingMode) {
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
