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
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.io.File;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.summerboot.jexpress.boot.config.ConfigChangeListener;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.ConfigUtil.ConfigLoadMode;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.boot.instrumentation.jmx.InstrumentationMgr;
import org.summerboot.jexpress.i18n.I18n;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPConfig;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.NioServer;
import org.summerboot.jexpress.util.BeanUtil;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class SummerApplication extends BootCLI {

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
        run(args, userOverrideModule);
    }

    /**
     *
     * @param args
     * @param userOverrideModule
     */
    public static void run(String[] args, Module userOverrideModule) {
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
        run(callerClass, userOverrideModule, args);
    }

    /**
     *
     * @param callerClass
     * @param userOverrideModule
     * @param args
     */
    public static void run(Class callerClass, Module userOverrideModule, String[] args) {
        String unittestWorkingDir = null;
        SummerApplication app = new SummerApplication(callerClass, userOverrideModule, unittestWorkingDir, args) {

            @Override
            protected void buildCLIOptions(Options options) {
            }

            @Override
            protected void runCLI(CommandLine cli, File cfgConfigDir) {
            }

            @Override
            protected void locadCustomizedConfigs(File configFolder) {
            }

            @Override
            protected void beforeStart(File configFolder, CommandLine cli) throws Exception {
            }
        };
        app.init().start();
    }

    /**
     *
     * @param workingDir
     * @param callerClass
     * @param userOverrideModule
     * @param argsStr
     * @return
     */
    public static SummerApplication unittest(String workingDir, Class callerClass, Module userOverrideModule, String argsStr) {
        String[] args = argsStr.split(" ");
        return unittest(workingDir, callerClass, userOverrideModule, args);
    }

    /**
     *
     * @param workingDir
     * @param callerClass
     * @param userOverrideModule
     * @param args
     * @return
     */
    public static SummerApplication unittest(String workingDir, Class callerClass, Module userOverrideModule, String... args) {
        SummerApplication app = new SummerApplication(callerClass, userOverrideModule, workingDir, args) {

            @Override
            protected void buildCLIOptions(Options options) {
            }

            @Override
            protected void runCLI(CommandLine cli, File cfgConfigDir) {
            }

            @Override
            protected void locadCustomizedConfigs(File configFolder) {
            }

            @Override
            protected void beforeStart(File configFolder, CommandLine cli) throws Exception {
            }
        };
        app.init();
        return app;
    }

    private final Module userOverrideModule;
    private Injector guiceInjector;

    protected SummerApplication() {
        this(null, null, null);
    }

    private SummerApplication(Class callerClass, Module userOverrideModule, String unittestWorkingDir, String... args) {
        super(callerClass, unittestWorkingDir, args);
        this.userOverrideModule = userOverrideModule;
    }

    public Injector getGuiceInjector() {
        return guiceInjector;
    }

    /**
     * IoC container initialization should happened after CLI and load
     * configuration, it will called when BootCLI.CLI_USE_IMPL result is ready
     *
     * @param userSpecifiedImplTags
     */
    @Override
    protected void onUserSpecifiedImplTagsReady(Set<String> userSpecifiedImplTags) {
        BootGuiceModule defaultBootModule = new BootGuiceModule(this, callerClass, scanedComponentBbindingMap, userSpecifiedImplTags, memo);
        Module guiceModule = userOverrideModule == null
                ? defaultBootModule
                : Modules.override(defaultBootModule).with(userOverrideModule);
//        if (bindingChannelHandlerClass != null) {
//            Module enabledModule = new Module() {
//                @Override
//                protected void configure() {
//                    if (bindingChannelHandlerClass != null) {
//                        bind(ChannelHandler.class).annotatedWith(Names.named(bindingChannelHandlerBindingName)).to(bindingChannelHandlerClass);
//                    }
//                }
//            };
//            guiceModule = Modules.override(guiceModule).with(enabledModule);
//        }
        if (userOverrideModule == null) {
            memo.append("\n\t- init default Ioc @Conponent");
        } else {
            memo.append("\n\t- init user overridden Ioc @Conponent via").append(userOverrideModule.getClass().getName());
        }

        // Guice.createInjector(module) --> BootGuiceModule.configure() --> this will trigger BootCLI.callbackGuice_scanAnnotation_Controller
        guiceInjector = Guice.createInjector(guiceModule);
        NioConfig.instance(NioConfig.class).setGuiceInjector(guiceInjector);
    }

    /**
     *
     * @return
     */
    public SummerApplication init() {
        /*
         * 1. init locale
         */
        I18n.init(getAddtionalI18n());

        /*
         * 2. load configs： 
         * all configs depend on BootCLI.CLI_CONFIG_TAG result
         * AuthConfig depends on Ioc scan result: JaxRsRequestProcessor scan @DeclareRoles to verify Role-Mapping in configuration file
         */
        loadBootConfigs(ConfigLoadMode.app_run, configChangeListener);
        locadCustomizedConfigs(cfgConfigDir);
        if (configChangeListener != null) {
            ConfigUtil.setConfigChangeListener(configChangeListener);
        }

        return this;
    }

    /**
     *
     * @return i18n class
     */
    protected Class getAddtionalI18n() {
        return null;
    }

    /**
     * callback to initialize based on customized config files in configDir
     *
     * @param configFolder
     */
    abstract protected void locadCustomizedConfigs(File configFolder);

    @Inject
    protected PostOffice postOffice;

    @Inject
    protected HealthInspector healthInspector;

    @Inject
    protected ConfigChangeListener configChangeListener;

    @Inject
    protected InstrumentationMgr instrumentationMgr;

    /**
     * run application with ping enabled, URI as webApiContextRoot +
     * loadBalancerHealthCheckPath
     *
     */
    public void start() {
        memo.append("\n\t- start: PostOffice=").append(postOffice.getClass().getName());
        memo.append("\n\t- start: HealthInspector=").append(healthInspector.getClass().getName());
        memo.append("\n\t- start: ConfigChangeListener=").append(configChangeListener.getClass().getName());
        memo.append("\n\t- start: InstrumentationMgr=").append(instrumentationMgr.getClass().getName());
        log.debug(() -> memo.toString());
        //1. init email
        final SMTPConfig smtpCfg = SMTPConfig.instance(SMTPConfig.class);
        if (postOffice != null) {
            postOffice.setAppVersion(super.appVersionLong);
            //gracefully shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (postOffice != null) {
                    postOffice.sendAlertSync(smtpCfg.getEmailToAppSupport(), "Shutdown at " + OffsetDateTime.now() + " - " + super.appVersionLong, "EOM", null, false);
                }
            }, "ShutdownHook.BootApp")
            );
        }
        try {
            //2. preLaunch
            beforeStart(cfgConfigDir, cli);

            //3. initialize JMX instrumentation
            if (instrumentationMgr != null/* && isJMXRequired()*/) {
                instrumentationMgr.start(BootConstant.VERSION);
            }

            //4. health inspection
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

            //5. run HTTP listening
            if (hasControllers) {
                NioServer.bind();
            }

            //6. announcement
            log.info(() -> I18n.info.launched.format(userSpecifiedResourceBundle, super.appVersionLong + " pid#" + BootConstant.PID));

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

    /**
     * callback before NIO binding
     *
     * @param configFolder
     * @param cli
     * @throws java.lang.Exception
     */
    abstract protected void beforeStart(File configFolder, final CommandLine cli) throws Exception;
}
