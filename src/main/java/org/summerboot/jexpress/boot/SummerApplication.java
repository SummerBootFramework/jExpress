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
package org.summerboot.jexpress.boot;

import com.google.inject.Inject;
import com.google.inject.Module;
import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.quartz.SchedulerException;
import org.summerboot.jexpress.boot.config.ConfigChangeListener;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.boot.instrumentation.NIOStatusListener;
import org.summerboot.jexpress.boot.instrumentation.Timeout;
import org.summerboot.jexpress.boot.instrumentation.jmx.InstrumentationMgr;
import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.integration.quartz.QuartzUtil;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;
import org.summerboot.jexpress.nio.grpc.GRPCServer;
import org.summerboot.jexpress.nio.grpc.GRPCServerConfig;
import org.summerboot.jexpress.nio.grpc.StatusReporter;
import org.summerboot.jexpress.nio.server.NioChannelInitializer;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.NioServer;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.BeanUtil;

import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * In Code We Trust
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class SummerApplication extends SummerBigBang {

    @Inject
    protected ConfigChangeListener configChangeListener;
    @Inject
    protected InstrumentationMgr instrumentationMgr;
    @Inject
    protected HealthInspector healthInspector;
    protected NioServer httpServer;
    protected List<GRPCServer> gRPCServerList = new ArrayList();
    @Inject
    protected PostOffice postOffice;
    private boolean memoLogged = false;

    private SummerApplication(Class callerClass, Module userOverrideModule, String... args) {
        super(callerClass, userOverrideModule, args);
    }

    /**
     * Might not work on Non HotSpot VM implementations.
     *
     * @param <T>
     * @return
     */
    public static <T extends SummerApplication> T run() {
        Module userOverrideModule = null;
        return run(userOverrideModule);
    }

    /**
     * @param callerClass
     * @param args
     */
    public static void run(Class callerClass, String[] args) {
        Module userOverrideModule = null;
        run(callerClass, userOverrideModule, args);
    }

    /**
     * Might not work on Non Hotspot VM implementations.
     *
     * @param <T>
     * @param userOverrideModule
     * @return
     */
    public static <T extends SummerApplication> T run(Module userOverrideModule) {
        String[] mainCommand = ApplicationUtil.getApplicationArgs();
        int size = mainCommand.length;
        String[] args = size > 0 ? new String[size - 1] : ApplicationUtil.EMPTY_ARGS;
        String mainClassName = mainCommand[0];
        System.arraycopy(mainCommand, 1, args, 0, size - 1);
        Class callerClass = null;
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if ("main".equals(stackTraceElement.getMethodName())) {
                try {
                    callerClass = Class.forName(stackTraceElement.getClassName());
                    break;
                } catch (ClassNotFoundException ex) {
                }
            }
        }
        if (callerClass == null) {
            try {
                callerClass = Class.forName(mainClassName);
            } catch (ClassNotFoundException ex) {
            }
        }
        if (callerClass == null) {
            throw new RuntimeException("Failed to find the caller class");
        }
        return run(callerClass, userOverrideModule, args);
    }

    /**
     * @param <T>
     * @param args
     * @return
     */
    public static <T extends SummerApplication> T run(String[] args) {
        Module userOverrideModule = null;
        return run(userOverrideModule, args);
    }

    /**
     * @param <T>
     * @param args
     * @param userOverrideModule
     * @return
     */
    public static <T extends SummerApplication> T run(Module userOverrideModule, String[] args) {
        Class callerClass = null;
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            if ("main".equals(stackTraceElement.getMethodName())) {
                try {
                    callerClass = Class.forName(stackTraceElement.getClassName());
                    break;
                } catch (ClassNotFoundException ex) {
                }
            }
        }
        if (callerClass == null) {
            throw new RuntimeException("Failed to find the caller class");
        }
        return run(callerClass, userOverrideModule, args);
    }

    /**
     * @param <T>
     * @param callerClass
     * @param userOverrideModule
     * @param argsStr
     * @return
     */
    public static <T extends SummerApplication> T run(Class callerClass, Module userOverrideModule, String argsStr) {
        String[] args = argsStr.split(" ");
        return run(callerClass, userOverrideModule, args);
    }

    /**
     * @param <T>
     * @param callerClass
     * @param userOverrideModule
     * @param args
     * @return
     */
    public static <T extends SummerApplication> T run(Class callerClass, Module userOverrideModule, String[] args) {
        SummerApplication app = new SummerApplication(callerClass, userOverrideModule, args) {
        };
        app.start();
        return (T) app;
    }

    /**
     * @param <T>
     * @param callerClass
     * @param userOverrideModule
     * @param argsStr
     * @return
     */
    public static <T extends SummerApplication> T unittest(Class callerClass, Module userOverrideModule, String argsStr) {
        String[] args = argsStr.split(" ");
        return unittest(callerClass, userOverrideModule, args);
    }

    /**
     * @param <T>
     * @param callerClass
     * @param userOverrideModule
     * @param args
     * @return
     */
    public static <T extends SummerApplication> T unittest(Class callerClass, Module userOverrideModule, String... args) {
        SummerApplication app = new SummerApplication(callerClass, userOverrideModule, args) {
        };
        app.traceConfig();
        return (T) app;
    }

    public List<GRPCServer> getgRPCServers() {
        return gRPCServerList;
    }

    @Override
    protected Class getAddtionalI18n() {
        return null;
    }

    protected void traceConfig() {
        log.trace("");
        if (!memoLogged) {
            memo.append(BootConstant.BR).append("\t- sys.prop.").append(BootConstant.SYS_PROP_LOGID).append(" = ").append(System.getProperty(BootConstant.SYS_PROP_LOGID));
            memo.append(BootConstant.BR).append("\t- sys.prop.").append(BootConstant.SYS_PROP_LOGFILEPATH).append(" = ").append(System.getProperty(BootConstant.SYS_PROP_LOGFILEPATH));
            memo.append(BootConstant.BR).append("\t- sys.prop.").append(BootConstant.SYS_PROP_LOGFILENAME).append(" = ").append(System.getProperty(BootConstant.SYS_PROP_LOGFILENAME));
            memo.append(BootConstant.BR).append("\t- sys.prop.").append(BootConstant.SYS_PROP_SERVER_NAME).append(" = ").append(System.getProperty(BootConstant.SYS_PROP_SERVER_NAME));
            memo.append(BootConstant.BR).append("\t- sys.prop.").append(BootConstant.SYS_PROP_APP_PACKAGE_NAME).append(" = ").append(System.getProperty(BootConstant.SYS_PROP_APP_PACKAGE_NAME));

            memo.append(BootConstant.BR).append("\t- start: PostOffice=").append(postOffice.getClass().getName());
            memo.append(BootConstant.BR).append("\t- start: HealthInspector=").append(healthInspector.getClass().getName());
            //memo.append(BootConstant.BR).append("\t- start: ConfigChangeListener=").append(configChangeListener.getClass().getName());
            memo.append(BootConstant.BR).append("\t- start: InstrumentationMgr=").append(instrumentationMgr.getClass().getName());
            memoLogged = true;
        }
        log.trace(() -> memo.toString());
    }

    /**
     * run application with ping enabled, URI as webApiContextRoot +
     * loadBalancerHealthCheckPath
     */
    public void start() {
        log.trace("");
        traceConfig();
        if (configChangeListener != null) {
            ConfigUtil.setConfigChangeListener(configChangeListener);
        }

        //1. init email
        log.trace("1. init email");
        final SMTPClientConfig smtpCfg = SMTPClientConfig.cfg;
        if (postOffice != null) {
            HealthMonitor.setPostOffice(postOffice);
            postOffice.setAppVersion(super.appVersion);
            //gracefully shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        if (postOffice != null) {
                            postOffice.sendAlertSync(smtpCfg.getEmailToAppSupport(), "Shutdown at " + OffsetDateTime.now() + " - " + super.appVersion, "EOM", null, false);
                        }
                    }, "ShutdownHook.BootApp")
            );
        }
        try {
            // 2. initialize JMX instrumentation
            log.trace("2. initialize JMX instrumentation");
            if (instrumentationMgr != null/* && isJMXRequired()*/) {
                instrumentationMgr.start(BootConstant.VERSION);
            }

            // 3a. runner.run
            log.trace("3a. runner.run");
            SummerRunner.RunnerContext context = new SummerRunner.RunnerContext(cli, userSpecifiedConfigDir, guiceInjector, healthInspector, postOffice);
            for (SummerRunner summerRunner : summerRunners) {
                summerRunner.run(context);
            }
            // 3b. start scheduler
            log.trace("3b. start scheduler");
            if (schedulerTriggers > 0) {
                scheduler.start();
                StringBuilder sb = new StringBuilder();
                sb.append("Scheduled jobs next fire time by ").append(schedulerTriggers).append(" triggers: ");
                QuartzUtil.getNextFireTimes(scheduler, sb);
                log.info(() -> sb.toString());
            }

            long timeoutMs = BackOffice.agent.getProcessTimeoutMilliseconds();
            String timeoutDesc = BackOffice.agent.getProcessTimeoutAlertMessage();
            // 4. health inspection
            log.trace("4. health inspection");
            StringBuilder sb = new StringBuilder();
            sb.append(BootConstant.BR).append(HealthMonitor.PROMPT);
            if (healthInspector != null) {
                try (var a = Timeout.watch(healthInspector.getClass().getName() + ".ping()", timeoutMs).withDesc(timeoutDesc)) {
                    List<Error> errors = healthInspector.ping(log);
                    if (errors == null || errors.isEmpty()) {
                        sb.append("passed");
                        log.info(sb);
                    } else {
                        String inspectionReport;
                        try {
                            inspectionReport = BeanUtil.toJson(errors, true, true);
                        } catch (Throwable ex) {
                            inspectionReport = "total " + errors.size();
                        }
                        sb.append(inspectionReport);
                        HealthMonitor.setHealthStatus(false, sb.toString(), healthInspector);
                    }
                }
            } else {
                sb.append("skipped");
                log.warn(sb);
            }

            // 5a. start server: gRPC
            if (hasGRPCImpl) {
                log.trace("5a. start server: gRPC hasGRPCImpl.bs={}", gRPCBindableServiceImplClasses);
                log.trace("5a. start server: gRPC hasGRPCImpl.ssd={}", gRPCServerServiceDefinitionImplClasses);
                ServerInterceptor serverInterceptor = super.guiceInjector.getInstance(ServerInterceptor.class);
                //2. init gRPC server
                GRPCServerConfig gRPCCfg = GRPCServerConfig.cfg;
                List<InetSocketAddress> bindingAddresses = gRPCCfg.getBindingAddresses();
                NIOStatusListener nioListener = super.guiceInjector.getInstance(NIOStatusListener.class);
                for (InetSocketAddress bindingAddress : bindingAddresses) {
                    String host = bindingAddress.getAddress().getHostAddress();
                    int port = bindingAddress.getPort();
                    log.trace("5a. binding gRPC on {}:{}", host, port);
                    try (var a = Timeout.watch("starting gRPCServer at " + host + ":" + port, timeoutMs).withDesc(timeoutDesc)) {
                        GRPCServer gRPCServer = new GRPCServer(host, port, gRPCCfg.getKmf(), gRPCCfg.getTmf(), serverInterceptor, gRPCCfg.getTpe(), nioListener);
                        ServerBuilder serverBuilder = gRPCServer.getServerBuilder();
                        for (Class<? extends BindableService> c : gRPCBindableServiceImplClasses) {
                            BindableService impl = guiceInjector.getInstance(c);
                            serverBuilder.addService(impl);
                            if (impl instanceof StatusReporter) {
                                ((StatusReporter) impl).setCounter(gRPCServer.getServiceCounter());
                            }
                        }
                        for (Class<ServerServiceDefinition> c : gRPCServerServiceDefinitionImplClasses) {
                            ServerServiceDefinition impl = guiceInjector.getInstance(c);
                            serverBuilder.addService(impl);
                        }
                        if (gRPCCfg.isAutoStart()) {
                            gRPCServer.start();
                        }
                        gRPCServerList.add(gRPCServer);
                    }
                }
            }

            // 5b. start server: HTTP
            log.trace("5b. start server: HTTP hasControllers={}", hasControllers);
            if (hasControllers && NioConfig.cfg.isAutoStart()) {
                try (var a = Timeout.watch("starting Web Server", timeoutMs).withDesc(timeoutDesc)) {
                    NioChannelInitializer channelInitializer = super.guiceInjector.getInstance(NioChannelInitializer.class);
                    NIOStatusListener nioListener = super.guiceInjector.getInstance(NIOStatusListener.class);
                    httpServer = new NioServer(channelInitializer.init(guiceInjector, channelHandlerNames), nioListener);
                    httpServer.bind(NioConfig.cfg);
                }
            }

            // 6. announcement
            log.info(() -> I18n.info.launched.format(userSpecifiedResourceBundle, appVersion + " pid#" + BootConstant.PID));

            String fullConfigInfo = sb.toString();
            if (postOffice != null) {
                postOffice.sendAlertAsync(smtpCfg.getEmailToAppSupport(), "Started at " + OffsetDateTime.now(), fullConfigInfo, null, false);
            }
        } catch (java.net.BindException ex) {// from NioServer
            log.fatal(ex + BootConstant.BR + BackOffice.agent.getPortInUseAlertMessage());
            System.exit(1);
        } catch (Throwable ex) {
            Throwable cause = ExceptionUtils.getRootCause(ex);
            if (cause instanceof java.net.BindException) {// from gRPC server
                log.fatal(ex + BootConstant.BR + BackOffice.agent.getPortInUseAlertMessage());
            } else {
                log.fatal(I18n.info.unlaunched.format(userSpecifiedResourceBundle), ex);
            }
            System.exit(1);
        }
    }

    public void shutdown() {
        log.trace("");
        if (gRPCServerList != null && !gRPCServerList.isEmpty()) {
            for (GRPCServer gRPCServer : gRPCServerList) {
                gRPCServer.shutdown();
            }
        }
        if (httpServer != null) {
            httpServer.shutdown();
        }
        if (instrumentationMgr != null) {
            instrumentationMgr.shutdown();
        }
        if (scheduler != null) {
            try {
                scheduler.shutdown();
            } catch (SchedulerException ex) {
                log.warn("Failed to shoutdown scheduler", ex);
            }
        }
    }
}
