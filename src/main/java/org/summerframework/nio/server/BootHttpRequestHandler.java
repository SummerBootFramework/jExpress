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

import org.summerframework.boot.BootErrorCode;
import org.summerframework.boot.BootPOI;
import org.summerframework.boot.instrumentation.HealthInspector;
import org.summerframework.integration.smtp.PostOffice;
import org.summerframework.integration.smtp.SMTPConfig;
import org.summerframework.nio.server.domain.Error;
import org.summerframework.nio.server.domain.ServiceResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.naming.NamingException;
import javax.persistence.PersistenceException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 * @version 2.0
 */
@Singleton
public class BootHttpRequestHandler extends NioServerHttpRequestHandler {

    @Inject
    protected PostOffice po;

    @Inject
    protected HealthInspector healthInspector;

    @Override
    protected void service(final ChannelHandlerContext ctx, final HttpHeaders httpRequestHeaders, final HttpMethod httptMethod,
            final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceResponse response) {
        RequestProcessor processor = null;
        try {
            // step1. find controller and the action in it
            processor = getRequestProcessor(httptMethod, httpRequestPath);
            if (processor == null) {
                processor = getRequestProcessor(httptMethod, "");
                if (processor == null) {
                    onActionNotFound(ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, response);
                    return;
                }
            }

            // step2. caller authentication, will do authorization in next step(ControllerAction.process(...))
            response.timestampPOI(BootPOI.AUTH_BEGIN);
            if (!authenticateCaller(processor, httpRequestHeaders, httpRequestPath, response)) {
                return;
            }

            // step3. serve the request, most frequently called first
            response.timestampPOI(BootPOI.PROCESS_BEGIN);
            processor.process(ctx, httpRequestHeaders, httpRequestPath, queryParams, httpPostRequestBody, response, BootErrorCode.NIO_WSRS_REQUEST_BAD_DATA);
            //} catch (ExpiredJwtException | SignatureException | MalformedJwtException ex) {
            //    nak(response, HttpResponseStatus.UNAUTHORIZED, BootErrorCode.AUTH_INVALID_TOKEN, "Invalid JWT");
        } catch (NamingException ex) {
            nakFatal(response, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.ACCESS_ERROR_LDAP, "Cannot access LDAP", ex, SMTPConfig.CFG.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);
        } catch (PersistenceException ex) {
            nakFatal(response, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.ACCESS_ERROR_DATABASE, "Cannot access database", ex, SMTPConfig.CFG.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);
        } catch (HttpConnectTimeoutException ex) {// a connection, over which an HttpRequest is intended to be sent, is not successfully established within a specified time period.
            nak(response, HttpResponseStatus.GATEWAY_TIMEOUT, BootErrorCode.HTTPCLIENT_TIMEOUT, ex.getMessage());
            response.level(Level.WARN);
        } catch (HttpTimeoutException ex) {// a response is not received within a specified time period.            
            // 504 will trigger terminal retry
            nak(response, HttpResponseStatus.GATEWAY_TIMEOUT, BootErrorCode.HTTPREQUEST_TIMEOUT, ex.getMessage());
            response.level(Level.WARN);
        } catch (IOException | UnresolvedAddressException ex) {//SocketException, 
            Throwable rc = ExceptionUtils.getRootCause(ex);
            if (rc instanceof RejectedExecutionException) {
                nak(response, HttpResponseStatus.SERVICE_UNAVAILABLE, BootErrorCode.HTTPCLIENT_TOO_MANY_CONNECTIONS_REJECT, ex.getMessage());
                response.level(Level.WARN);
            } else {
                startHealthInspectionSingleton(NioConfig.CFG.getHealthInspectionIntervalSeconds(), ex);
                nakFatal(response, HttpResponseStatus.SERVICE_UNAVAILABLE, BootErrorCode.IO_ERROR, "IO Failure", ex, SMTPConfig.CFG.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            nakFatal(response, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.APP_INTERRUPTED, "Service Interrupted", ex, SMTPConfig.CFG.getEmailToDevelopment(), httptMethod + " " + httpRequestPath);
        } catch (Throwable ex) {
            onUnexpectedException(ex, processor, ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, response);
        } finally {
            onControllerActionFinally(processor, ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, response);
            response.timestampPOI(BootPOI.PROCESS_END);
        }
    }

    protected void onActionNotFound(final ChannelHandlerContext ctx, final HttpHeaders httpRequestHeaders, final HttpMethod httptMethod,
            final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceResponse response) {
        response.status(HttpResponseStatus.NOT_FOUND).error(new Error(BootErrorCode.AUTH_INVALID_URL, "path not found", httptMethod + " " + httpRequestPath, null));
    }

    protected void onUnexpectedException(Throwable ex, RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceResponse response) {
        nakFatal(response, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.NIO_UNEXPECTED_FAILURE, "Unexpected Failure/Bug?", ex, SMTPConfig.CFG.getEmailToDevelopment(), httptMethod + " " + httpRequestPath);
    }

    protected void onControllerActionFinally(RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceResponse response) {
        if (processor != null && processor.isRoleBased()) {
            httpRequestHeaders.set(HttpHeaderNames.AUTHORIZATION, "Bearer token-verified");// protect auth token from being logged
        }
    }

    protected boolean authenticateCaller(final RequestProcessor processor, final HttpHeaders httpRequestHeaders, final String httpRequestPath, final ServiceResponse response) throws Exception {
        return true;
    }

    @Override
    protected String scrapeLogging(String log) {
        return log;
    }

    @Override
    protected void onServiceFinal_ResponseSent_LogSaved(final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
            final ServiceResponse response, long queuingTime, long processTime, long responseTime, long responseContentLength, String report, Throwable ioEx) throws Exception {
    }

    private static final ThreadPoolExecutor POOL;

    static {
        POOL = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1), Executors.defaultThreadFactory(), new ThreadPoolExecutor.DiscardPolicy());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            POOL.shutdown();
        }, "ShutdownHook.BootHttpRequestHandler")
        );
    }

    protected void startHealthInspectionSingleton(int inspectionIntervalSeconds, Throwable cause) {
        if (healthInspector == null) {
            return;
        }
        long i = HealthInspector.healthInspectorCounter.incrementAndGet();
        if (i > 1) {
            return;
        }
        Runnable asyncTask = () -> {
            HealthInspector.healthInspectorCounter.incrementAndGet();
            boolean inspectionFailed;
            do {
                List<Error> errors = healthInspector.ping(true, log);
                inspectionFailed = errors != null && !errors.isEmpty();
                if (inspectionFailed) {
                    try {
                        TimeUnit.SECONDS.sleep(inspectionIntervalSeconds);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            } while (inspectionFailed);
            HealthInspector.healthInspectorCounter.set(0);
        };
        if (POOL.getActiveCount() < 1) {
            try {
                POOL.execute(asyncTask);
            } catch (RejectedExecutionException ex2) {
                log.debug("Duplicated HealthInspection Rejected");
            }
        } else {
            log.debug("HealthInspection Skipped");
        }
    }

    protected void nak(ServiceResponse response, HttpResponseStatus httpResponseStatus, int appErrorCode, String errorMessage) {
        // 1. convert to JSON
        Error e = new Error(appErrorCode, null, errorMessage, null);
        // 2. build JSON response with same app error code, and keep the default INFO log level.
        response.status(httpResponseStatus).error(e);
    }

    /**
     * Build negative acknowledgement response with exception at ERROR level
     * when ex is not null
     *
     * @param response
     * @param httpResponseStatus
     * @param appErrorCode
     * @param errorMessage
     * @param ex
     */
    protected void nakError(ServiceResponse response, HttpResponseStatus httpResponseStatus, int appErrorCode, String errorMessage, Throwable ex) {
        // 1. convert to JSON
        //Error e = new ServiceError(appErrorCode, null, errorMessage, ex);
        Error e = new Error(appErrorCode, null, errorMessage, ex);
        // 2. build JSON response with same app error code and exception, and Level.ERROR is used as the default log level when exception is not null, 
        // the log level will be set to INFO once the exception is null.
        response.status(httpResponseStatus).error(e);
    }

    /**
     * Build negative acknowledgement response with exception at FATAL level, no
     * matter ex is null or not
     *
     * @param response
     * @param httpResponseStatus
     * @param appErrorCode
     * @param errorMessage
     * @param ex
     * @param emailTo
     * @param content
     */
    protected void nakFatal(ServiceResponse response, HttpResponseStatus httpResponseStatus, int appErrorCode, String errorMessage, Throwable ex, Collection<String> emailTo, String content) {
        // 1. build JSON response with same app error code and exception
        nakError(response, httpResponseStatus, appErrorCode, errorMessage, ex);
        // 2. set log level to FATAL
        response.level(Level.FATAL);
        // 3. send sendAlertAsync
        if (po != null) {
            // build email content
            String briefContent = "caller=" + response.callerId() + ", request#" + response.hit() + ": " + content;
            po.sendAlertAsync(emailTo, errorMessage, briefContent, ex, true);
        }
    }
}
