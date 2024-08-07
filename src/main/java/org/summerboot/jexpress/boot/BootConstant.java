<?xml version="1.0" encoding="UTF-8"?>
<!-- version 2.4.10 -->
<Configuration status="WARN" monitorInterval="30">
    <Properties>
        <Property name="logPattern" value="[${sys:logId}] %d{ISO8601} %p %c{}.%M() [%t] %m %ex%n"/>
        <!-- usage: %replace{pattern}{regex}{substitution} -->
        <Property name="logPattern2" value="[${sys:logId}] %d{ISO8601} %p %c{}.%M() [%t] %replace{%enc{%m}{CRLF}}{\\r|\\n|%0D|%0A|%0d|%0a}{|} %uEx%n"/>
    </Properties>
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <!-- <PatternLayout pattern="%d{ISO8601}{Canada/Eastern} %p %c{}.%M() [%t] %enc{%m}{CRLF} %ex%n"/>-->
            <PatternLayout pattern="${sys:logPattern}"/>
        </Console>

        <!-- 
            1. Requires JVM arg: -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector 
            2. Required total disk spece: around 200MB
            3. Archive logs: by DAY and split them by MINUTE
            4. Default log level is tuned for development
        -->
        <RollingRandomAccessFile
                name="StatusLogFile"
                fileName="${sys:logPath}/${sys:appName}_status_${sys:serverName}.log"
                filePattern="${sys:logPath}/$${date:yyyy-MM-dd}/${sys:appName}_status_${sys:serverName}_%d{yyyy-MM-dd HH:mm}.%i.log.gz"
                immediateFlush="false"
                ignoreExceptions="false">
            <PatternLayout pattern="${sys:logPattern}"/>
            <Policies>
                <SizeBasedTriggeringPolicy size="2MB"/>
            </Policies>
            <DefaultRolloverStrategy max="50"/>
        </RollingRandomAccessFile>
        <RollingRandomAccessFile
                name="RequestLogFile"
                fileName="${sys:logPath}/${sys:appName}_requests_${sys:serverName}.log"
                filePattern="${sys:logPath}/$${date:yyyy-MM-dd}/${sys:appName}_requests_${sys:serverName}_%d{yyyy-MM-dd HH:mm}.%i.log.gz"
                immediateFlush="false"
                ignoreExceptions="false">
            <PatternLayout pattern="${sys:logPattern}"/>
            <Policies>
                <SizeBasedTriggeringPolicy size="10MB"/>
            </Policies>
            <DefaultRolloverStrategy max="500"/>
        </RollingRandomAccessFile>
        <RollingRandomAccessFile
                name="ScheduledLogFile"
                fileName="${sys:logPath}/${sys:appName}_schedule_${sys:serverName}.log"
                filePattern="${sys:logPath}/$${date:yyyy-MM-dd}/${sys:appName}_schedule_${sys:serverName}_%d{yyyy-MM-dd HH:mm}.%i.log.gz"
                immediateFlush="false"
                ignoreExceptions="false">
            <PatternLayout pattern="${sys:logPattern}"/>
            <Policies>
                <SizeBasedTriggeringPolicy size="10MB"/>
            </Policies>
            <DefaultRolloverStrategy max="500"/>
        </RollingRandomAccessFile>
    </Appenders>
    <Loggers>
        <Root level="warn" includeLocation="true">
            <AppenderRef ref="StatusLogFile"/>
            <!-- -->
            <AppenderRef ref="Console"/>
        </Root>
        <Logger level="info" name="com.hazelcast" includeLocation="true"/>
        <Logger level="warn" name="io.netty" includeLocation="true"/>
        <!-- Note: "java.lang.UnsupportedOperationException: sun.misc.Unsafe unavailable" can be seen, also can be ignored, when -Dio.netty.noUnsafe=true and io.netty.util.internal.CleanerJava9 log level is below INFO (either TRACE or DEBUG) -->
        <Logger level="info" name="io.netty.util.internal.CleanerJava9" includeLocation="true"/>
        <Logger level="debug" name="org.summerboot.jexpress.boot" includeLocation="true"/>
        <Logger level="warn" org.summerboot.jexpress.boot.instrumentation.Timeout includeLocation="true"/>
        <Logger level="info" name="org.summerboot.jexpress.integration.httpclient.HttpClientConfig" includeLocation="true"/>
        <Logger level="info" name="org.summerboot.jexpress.nio.server.NioServerHttpInitializer" includeLocation="true"/>
        <Logger level="debug" name="org.summerboot.jexpress.nio.server.NioServer" includeLocation="true"/>
        <Logger level="debug" name="org.summerboot.jexpress.nio.grpc.GRPCServer" includeLocation="true"/>


        <Logger level="warn" name="io.netty.handler" includeLocation="false" additivity="false">
            <AppenderRef ref="RequestLogFile"/>
            <!-- -->
            <AppenderRef ref="Console"/>
        </Logger>

        <Logger level="info" name="org.summerboot.jexpress.nio.server.BootHttpRequestHandler" includeLocation="false"
                additivity="false">
            <AppenderRef ref="RequestLogFile"/>
            <!-- -->
            <AppenderRef ref="Console"/>
        </Logger>
        <Logger level="info" name="org.summerboot.jexpress.integration.quartz.BootJobListener" includeLocation="false"
                additivity="false">
            <AppenderRef ref="ScheduledLogFile"/>
            <!-- -->
            <AppenderRef ref="Console"/>
        </Logger>
        <Logger level="warn" name="org.summerboot.jexpress.integration.ldap" includeLocation="true" additivity="false">
            <AppenderRef ref="RequestLogFile"/>
            <!-- -->
            <AppenderRef ref="Console"/>
        </Logger>
        <Logger level="debug" name="org.hibernate.SQL" includeLocation="true" additivity="false">
            <AppenderRef ref="RequestLogFile"/>
            <!-- -->
            <AppenderRef ref="Console"/>
        </Logger>
        <Logger level="debug" name="org.hibernate.type" includeLocation="true" additivity="false">
            <AppenderRef ref="RequestLogFile"/>
            <!-- -->
            <AppenderRef ref="Console"/>
        </Logger>

        <Logger level="debug" name="${sys:appPackageName}" includeLocation="true" additivity="false">
            <AppenderRef ref="RequestLogFile"/>
            <!-- -->
            <AppenderRef ref="Console"/>
        </Logger>
    </Loggers>
</Configuration>
