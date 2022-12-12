/*
 * Copyright 2005-2022 Du Law Office - The Summer Boot Framework Project
 *
 * The Summer Boot Project licenses this file to you under the Apache License, appVersionLong 2.0 (the
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

import com.google.inject.Module;
import com.google.inject.Inject;
import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import java.time.OffsetDateTime;
import java.util.List;
import org.summerboot.jexpress.boot.config.ConfigChangeListener;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.boot.instrumentation.jmx.InstrumentationMgr;
import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;
import org.summerboot.jexpress.nio.grpc.Counter;
import org.summerboot.jexpress.nio.grpc.GRPCServer;
import org.summerboot.jexpress.nio.grpc.GRPCServerConfig;
import org.summerboot.jexpress.nio.server.NioServer;
import org.summerboot.jexpress.util.BeanUtil;
import org.summerboot.jexpress.nio.grpc.StatusReporter;

/**
 * In Code We Trust
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class SummerApplication extends SummerBigBang {

    /**
     * Might not work on Non Hotspot VM implementations.
     */
    public static void run() {
        Module userOverrideModule = null;
        run(userOverrideModule);
    }

    /**
     *
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
     * @param userOverrideModule
     */
    public static void run(Module userOverrideModule) {
        String[] mainCommand = System.getProperty(SUN_JAVA_COMMAND).split(" ");
        String[] args = new String[mainCommand.length - 1];
        String mainClassName = mainCommand[0];
        for (int i = 1; i < mainCommand.length; i++) {
            args[i - 1] = mainCommand[i];
        }
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
        run(callerClass, userOverrideModule, args);
    }

    /**
     *
     * @param args
     */
    public static void run(String[] args) {
        Module userOverrideModule = null;
        run(userOverrideModule, args);
    }

    /**
     *
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
     *
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
     *
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
     *
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
     *
     * @param <T>
     * @param callerClass
     * @param userOverrideModule
     * @param args
     * @return
     */
    public static <T extends SummerApplication> T unittest(Class callerClass, Module userOverrideModule, String... args) {
        SummerApplication app = new SummerApplication(callerClass, userOverrideModule, args) {
        };
        return (T) app;
    }

    private SummerApplication(Class callerClass, Module userOverrideModule, String... args) {
        super(callerClass, userOverrideModule, args);
    }

    @Inject
    protected ConfigChangeListener configChangeListener;

    @Inject
    protected InstrumentationMgr instrumentationMgr;

    @Inject
    protected HealthInspector healthInspector;

    @Inject
    protected PostOffice postOffice;

    private GRPCServer gRPCServer;

    @Override
    protected Class getAddtionalI18n() {
        return null;
    }

    /**
     * run application with ping enabled, URI as webApiContextRoot +
     * loadBalancerHealthCheckPath
     *
     */
    public void start() {
        memo.append("\n\t- sys.prop.").append(SummerApplication.SYS_PROP_APP_VERSION).append("=").append(System.getProperty(SummerApplication.SYS_PROP_APP_VERSION));
        memo.append("\n\t- sys.prop.").append(SummerApplication.SYS_PROP_APP_PACKAGE_NAME).append("=").append(System.getProperty(SummerApplication.SYS_PROP_APP_PACKAGE_NAME));
        memo.append("\n\t- sys.prop.").append(SummerApplication.SYS_PROP_APP_NAME).append("=").append(System.getProperty(SummerApplication.SYS_PROP_APP_NAME));
        memo.append("\n\t- sys.prop.").append(SummerApplication.SYS_PROP_LOGGINGPATH).append("=").append(System.getProperty(SummerApplication.SYS_PROP_LOGGINGPATH));

        memo.append("\n\t- start: PostOffice=").append(postOffice.getClass().getName());
        memo.append("\n\t- start: HealthInspector=").append(healthInspector.getClass().getName());
        //memo.append("\n\t- start: ConfigChangeListener=").append(configChangeListener.getClass().getName());
        memo.append("\n\t- start: InstrumentationMgr=").append(instrumentationMgr.getClass().getName());
        log.trace(() -> memo.toString());

        if (configChangeListener != null) {
            ConfigUtil.setConfigChangeListener(configChangeListener);
        }

        //1. init email
        final SMTPClientConfig smtpCfg = SMTPClientConfig.cfg;
        if (postOffice != null) {
            postOffice.setAppVersion(super.appVersionLong);
            //gracefully GRPCServer
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (postOffice != null) {
                    postOffice.sendAlertSync(smtpCfg.getEmailToAppSupport(), "Shutdown at " + OffsetDateTime.now() + " - " + super.appVersionLong, "EOM", null, false);
                }
            }, "ShutdownHook.BootApp")
            );
        }
        try {
            //2. initialize JMX instrumentation
            if (instrumentationMgr != null/* && isJMXRequired()*/) {
                instrumentationMgr.start(BootConstant.VERSION);
            }

            //3. health inspection
            StringBuilder sb = new StringBuilder();
            sb.append(System.lineSeparator()).append(HealthMonitor.PROMPT);
            if (healthInspector != null) {
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
            } else {
                sb.append("skipped");
                log.warn(sb);
            }

            //4. runner.run            
            SummerRunner.RunnerContext context = new SummerRunner.RunnerContext(cli, userSpecifiedConfigDir, guiceInjector, healthInspector, postOffice);
            for (SummerRunner summerRunner : summerRunners) {
                summerRunner.run(context);
            }

            //5a. start server: gRPC
            log.trace("hasGRPCImpl.bs=" + gRPCBindableServiceImplClasses);
            log.trace("hasGRPCImpl.ssd=" + gRPCServerServiceDefinitionImplClasses);
            if (hasGRPCImpl) {
                //2. init gRPC server
                GRPCServerConfig gRPCCfg = GRPCServerConfig.cfg;
                gRPCServer = new GRPCServer(gRPCCfg.getBindingAddr(), gRPCCfg.getBindingPort(), gRPCCfg.getKmf(), gRPCCfg.getTmf());
                Counter gRPCCounter = gRPCServer.configThreadPool(gRPCCfg.getPoolCoreSize(), gRPCCfg.getPoolMaxSizeMaxSize(), gRPCCfg.getPoolQueueSize(), gRPCCfg.getKeepAliveSeconds());

                ServerBuilder serverBuilder = gRPCServer.serverBuilder();
                for (Class<? extends BindableService> c : gRPCBindableServiceImplClasses) {
                    BindableService impl = guiceInjector.getInstance(c);
                    serverBuilder.addService(impl);
                    if (impl instanceof StatusReporter) {
                        ((StatusReporter) impl).setCounter(gRPCCounter);
                    }
                }
                for (Class<ServerServiceDefinition> c : gRPCServerServiceDefinitionImplClasses) {
                    ServerServiceDefinition impl = guiceInjector.getInstance(c);
                    serverBuilder.addService(impl);
                }
                gRPCServer.start();
            }

            //5b. start server: HTTP
            log.trace("hasControllers=" + hasControllers);
            if (hasControllers) {
                NioServer.bind();
            }

            //6. announcement
            log.info(() -> I18n.info.launched.format(userSpecifiedResourceBundle, appVersionLong + " pid#" + BootConstant.PID));

            String fullConfigInfo = sb.toString();
            if (postOffice != null) {
                postOffice.sendAlertAsync(smtpCfg.getEmailToAppSupport(), "Started at " + OffsetDateTime.now(), fullConfigInfo, null, false);
            }
        } catch (java.net.BindException ex) {
            log.fatal("\nIn order to check which application is listening on a port, you can use the following command from the command line:\n"
                    + "\n"
                    + "For Microsoft Windows:\n"
                    + "    netstat -ano | find \"80\" | find \"LISTEN\"\n"
                    + "    tasklist /fi \"PID eq <pid>\"\n"
                    + "     \n"
                    + "For Linux:\n"
                    + "    netstat -anpe | grep \"80\" | grep \"LISTEN\" \n", ex);
            System.exit(1);
        } catch (Throwable ex) {
            log.fatal(I18n.info.unlaunched.format(userSpecifiedResourceBundle), ex);
            System.exit(1);
        }
    }

    public void stop() {
        if (gRPCServer != null) {
            gRPCServer.shutdown();
        }
        NioServer.shutdown();
        if (instrumentationMgr != null) {
            instrumentationMgr.shutdown();
        }
    }
}
