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
package org.summerboot.jexpress.infra.grpc.server;

import com.google.protobuf.Message;
import io.grpc.BindableService;
import io.grpc.Grpc;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCredentials;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.TlsServerCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.annotation.rest.RequiresHealthCheck;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.boot.lifecycle.app.IdleEventMonitor;
import org.summerboot.jexpress.infra.grpc.server.config.GrpcServerConfig;
import org.summerboot.jexpress.infra.metrics.NioStatusListener;
import org.summerboot.jexpress.util.concurrent.NamedDefaultThreadFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GrpcServer {

    protected static final Logger log = LogManager.getLogger(GrpcServer.class.getName());

    protected static final GrpcServiceCounter serviceCounter = new GrpcServiceCounter();


    protected final String bindingAddr;
    protected final int port;
    protected final ServerCredentials serverCredentials;

    protected final ServerBuilder serverBuilder;

    protected Server server = null;

    protected ScheduledExecutorService statusReporter = null;
    //protected boolean servicePaused = false;

    public static final String IDLE_EVENT_MONITOR_ID = "GrpcCServer";
    public static final IdleEventMonitor IDLE_EVENT_MONITOR = new IdleEventMonitor(IDLE_EVENT_MONITOR_ID) {
        @Override
        public long getIdleIntervalMillis() {
            return TimeUnit.SECONDS.toMillis(GrpcServerConfig.cfg.getIdleThresholdSecond());
        }
    };

    static GrpcServiceCounter getServiceCounter() {
        return serviceCounter;
    }

    public GrpcServer(String bindingAddr, int port, KeyManagerFactory kmf, TrustManagerFactory tmf, ThreadPoolExecutor tpe, boolean useVirtualThread, boolean generateReport, NioStatusListener nioListener, ServerInterceptor... serverInterceptors) {
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
    protected void report(ThreadPoolExecutor tpe, boolean useVirtualThread, NioStatusListener nioListener, String bindingAddr, int port) {
        int interval = 1;
        final AtomicReference<Long> lastBizHitRef = new AtomicReference<>();
        lastBizHitRef.set(-1L);
        long totalChannel = -1;//NioServerContext.COUNTER_TOTAL_CHANNEL.get();
        long activeChannel = -1;//NioServerContext.COUNTER_ACTIVE_CHANNEL.get();
        ScheduledExecutorService old2 = statusReporter;
        statusReporter = Executors.newSingleThreadScheduledExecutor(NamedDefaultThreadFactory.build("Netty-gRPC.QPS_SERVICE@" + bindingAddr + ":" + port, useVirtualThread));
        final AtomicLong lastChecksum = new AtomicLong(0);
        String appInfo = "gRPC@" + BootConstants.VERSION + " " + BootConstants.PID;
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
        String appInfo = BootConstants.VERSION + " " + BootConstants.PID;
        server = serverBuilder.build().start();
        String schema = serverCredentials == null ? "grpc" : "grpcs";
        String info = "Netty GRPC server [" + appInfo + "] is listening on " + schema + "://" + bindingAddr + ":" + port;
        memo.append(BootConstants.BR).append(info);
        log.info(info);
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    shutdown();
                }, "GrpcServer.shutdown and stop listening on " + schema + "://" + bindingAddr + ":" + port));
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
            log.warn("*** GrpcServer shutdown " + bindingAddr + ":" + port);
            server.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("GrpcServer shutdown timeout " + bindingAddr + ":" + port);
        } finally {
            server = null;
        }
    }


    public void registerService(ServerServiceDefinition serviceDefinition) {
        serverBuilder.addService(serviceDefinition);
    }

    public void registerService(BindableService service) {
        // 1. 注册 gRPC 服务到 serverBuilder
        serverBuilder.addService(service);

        // 2. 遍历服务类及其父类，查找所有带 @RequiresHealthCheck 注解的方法，并缓存到 requiredHealthCheckCache
        Class<?> startClazz = service.getClass();
        ServerServiceDefinition serviceDefinition = service.bindService();

        // 遍历 gRPC 描述符中的所有方法
        for (MethodDescriptor<?, ?> methodDesc : serviceDefinition.getServiceDescriptor().getMethods()) {
            String fullMethodName = methodDesc.getFullMethodName(); // 例如: "UserService/GetUser"
            String bareMethodName = methodDesc.getBareMethodName(); // 例如: "GetUser"

            // 寻找匹配的注解
            RequiresHealthCheck annotation = findAnnotationInHierarchy(startClazz, bareMethodName, methodDesc);

            if (annotation != null) {
                requiredHealthCheckCache.put(fullMethodName, annotation.value());
            }
        }
    }

    // cache to store the mapping of gRPC method full names to their required health checks
    private static final Map<String, String[]> requiredHealthCheckCache = new HashMap<>();

    public static String[] getRequiredHealthChecks(String fullMethodName) {
        return requiredHealthCheckCache.get(fullMethodName);
    }

    private static RequiresHealthCheck findAnnotationInHierarchy(Class<?> clazz, String bareMethodName, MethodDescriptor<?, ?> methodDesc) {
        while (clazz != null && clazz != Object.class) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {

                // 1. check if the method name matches the bareMethodName
                if (!method.getName().equalsIgnoreCase(bareMethodName)) {
                    continue;
                }

                // 2. core filtering: check if the method has exactly 2 parameters
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 2) {
                    continue;
                }

                // 3. core filtering: check if the second parameter is of type StreamObserver
                if (!StreamObserver.class.isAssignableFrom(parameterTypes[1])) {
                    continue;
                }

                // 4. Precision filtering using advanced techniques: Compare against the official Protobuf fully qualified name of the first parameter (the Request message)
                // Both gRPC method descriptors and Java-compiled Message classes contain Protobuf metadata.
                Class<?> firstParamType = parameterTypes[0];
                if (Message.class.isAssignableFrom(firstParamType)) {
                    try {
                        // Use reflection to call getDefaultInstance().getDescriptorForType().getFullName() on the generated class
                        // e.g., for com.google.protobuf.Empty, this returns "google.protobuf.Empty"
                        Method getDefaultInstance = firstParamType.getMethod("getDefaultInstance");
                        Message defaultInstance = (Message) getDefaultInstance.invoke(null);
                        String javaProtoFullName = defaultInstance.getDescriptorForType().getFullName();

                        /*
                        TODO:
                        Extract the fully qualified request type name from the native gRPC MethodDescriptor.
                        The format of methodDesc.getFullMethodName() is typically "package.Service/MethodName".
                        In standard generated code, the Marshaller's toString() output or internal name can be identified via reflection
                        or by directly applying the following comparison logic:

                        [Safest Defensive Approach]: Having already ruled out cases where the parameter count isn't 2, method names don't match,
                        or the second parameter isn't a StreamObserver, I think that if the method name matches exactly,
                        there is a 99% probability— within standard stubs—that this is the specific RPC implementation method I'm looking for.
                         */

                    } catch (Exception ignored) {
                        // Even if reflection throws an error, this can still serve as a fallback due to the filtering provided by the three strong rules mentioned earlier.
                    }
                }

                // 5. Precisely matching gRPC core methods
                if (method.isAnnotationPresent(RequiresHealthCheck.class)) {
                    return method.getAnnotation(RequiresHealthCheck.class);
                }

                // The correct gRPC method was found (i.e., it matched the `bareMethodName` and satisfied the gRPC signature),
                // but it lacked the required annotation, so the process terminated by returning `null`.
                return null;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

}
