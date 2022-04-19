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
import org.summerframework.nio.server.domain.ServiceContext;
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
import java.util.concurrent.RejectedExecutionException;
import javax.naming.NamingException;
import jakarta.persistence.PersistenceException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.summerframework.boot.instrumentation.HealthMonitor;

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
            final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceContext context) {
        RequestProcessor processor = null;
        try {
            // step1. find controller and the action in it
            processor = getRequestProcessor(httptMethod, httpRequestPath);
            if (processor == null) {
                processor = getRequestProcessor(httptMethod, "");
                if (processor == null) {
                    onActionNotFound(ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
                    return;
                }
            }

            // step2. caller authentication, will do authorization in next step(ControllerAction.process(...))
            context.timestampPOI(BootPOI.AUTH_BEGIN);
            if (!authenticateCaller(processor, httpRequestHeaders, httpRequestPath, context)) {
                context.status(HttpResponseStatus.UNAUTHORIZED);
                return;
            }

            // step3. serve the request, most frequently called first
            context.timestampPOI(BootPOI.PROCESS_BEGIN);
            processor.process(ctx, httpRequestHeaders, httpRequestPath, queryParams, httpPostRequestBody, context, BootErrorCode.NIO_WSRS_REQUEST_BAD_DATA);
            //} catch (ExpiredJwtException | SignatureException | MalformedJwtException ex) {
            //    nak(context, HttpResponseStatus.UNAUTHORIZED, BootErrorCode.AUTH_INVALID_TOKEN, "Invalid JWT");
        } catch (NamingException ex) {
            onNamingException(ex, httptMethod, httpRequestPath, context);
        } catch (PersistenceException ex) {
            onPersistenceException(ex, httptMethod, httpRequestPath, context);
        } catch (HttpConnectTimeoutException ex) {// a connection, over which an HttpRequest is intended to be sent, is not successfully established within a specified time period.
            onHttpConnectTimeoutException(ex, httptMethod, httpRequestPath, context);
        } catch (HttpTimeoutException ex) {// a context is not received within a specified time period.            
            onHttpTimeoutException(ex, httptMethod, httpRequestPath, context);
        } catch (RejectedExecutionException ex) {
            onRejectedExecutionException(ex, httptMethod, httpRequestPath, context);
        } catch (IOException | UnresolvedAddressException ex) {//SocketException, 
            Throwable rc = ExceptionUtils.getRootCause(ex);
            if (rc == null) {
                rc = ex;
            }
            if (rc instanceof RejectedExecutionException) {
                onRejectedExecutionException(rc, httptMethod, httpRequestPath, context);
            } else {
                onIOException(rc, httptMethod, httpRequestPath, context);
            }
        } catch (InterruptedException ex) {
            onInterruptedException(ex, httptMethod, httpRequestPath, context);
        } catch (Throwable ex) {
            onUnexpectedException(ex, processor, ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
        } finally {
            afterService(processor, ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
            context.timestampPOI(BootPOI.PROCESS_END);
        }
    }

    protected boolean authenticateCaller(final RequestProcessor processor, final HttpHeaders httpRequestHeaders, final String httpRequestPath, final ServiceContext context) throws Exception {
        return true;
    }

    protected void onActionNotFound(final ChannelHandlerContext ctx, final HttpHeaders httpRequestHeaders, final HttpMethod httptMethod,
            final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceContext context) {
        context.status(HttpResponseStatus.NOT_FOUND).error(new Error(BootErrorCode.AUTH_INVALID_URL, "path not found", httptMethod + " " + httpRequestPath, null));
    }

    protected void onNamingException(NamingException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context) {
        nakFatal(context, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.ACCESS_ERROR_LDAP, "Cannot access LDAP", ex, SMTPConfig.CFG.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);
    }

    protected void onPersistenceException(PersistenceException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context) {
        nakFatal(context, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.ACCESS_ERROR_DATABASE, "Cannot access database", ex, SMTPConfig.CFG.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);
    }

    /**
     * Happens when a connection, over which an HttpRequest is intended to be
     * sent, is not successfully established within a specified time period.
     *
     * @param ex
     * @param httptMethod
     * @param httpRequestPath
     * @param context
     */
    protected void onHttpConnectTimeoutException(HttpConnectTimeoutException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context) {
        nak(context, HttpResponseStatus.GATEWAY_TIMEOUT, BootErrorCode.HTTPCLIENT_TIMEOUT, ex.getMessage());
        context.level(Level.WARN);
    }

    /**
     * Happens when a context is not received within a specified time period.
     *
     * @param ex
     * @param httptMethod
     * @param httpRequestPath
     * @param context
     */
    protected void onHttpTimeoutException(HttpTimeoutException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context) {
        nak(context, HttpResponseStatus.GATEWAY_TIMEOUT, BootErrorCode.HTTPREQUEST_TIMEOUT, ex.getMessage());
        context.level(Level.WARN);
    }

    protected void onRejectedExecutionException(Throwable ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context) {
        nak(context, HttpResponseStatus.SERVICE_UNAVAILABLE, BootErrorCode.HTTPCLIENT_TOO_MANY_CONNECTIONS_REJECT, ex.getMessage());
        context.level(Level.WARN);
    }

    protected void onIOException(Throwable ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context) {
        HealthMonitor.setHealthStatus(false, ex.toString(), getHealthInspector());
        nakFatal(context, HttpResponseStatus.SERVICE_UNAVAILABLE, BootErrorCode.IO_ERROR, "IO Failure", ex, SMTPConfig.CFG.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);
    }

    protected HealthInspector getHealthInspector() {
        return healthInspector;
    }

    protected void onInterruptedException(InterruptedException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context) {
        Thread.currentThread().interrupt();
        nakFatal(context, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.APP_INTERRUPTED, "Service Interrupted", ex, SMTPConfig.CFG.getEmailToDevelopment(), httptMethod + " " + httpRequestPath);

    }

    protected void onUnexpectedException(Throwable ex, RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceContext context) {
        nakFatal(context, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.NIO_UNEXPECTED_FAILURE, "Unexpected Failure/Bug?", ex, SMTPConfig.CFG.getEmailToDevelopment(), httptMethod + " " + httpRequestPath);
    }

    protected void afterService(RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceContext context) {
        protectAuthToken(processor, httpRequestHeaders);
    }

    protected void protectAuthToken(RequestProcessor processor, HttpHeaders httpRequestHeaders) {
        if (processor != null && processor.isRoleBased()) {
            httpRequestHeaders.set(HttpHeaderNames.AUTHORIZATION, "***");// protect auth token from being logged
        }
    }

    @Override
    protected String beforeLogging(String log) {
        return log;
    }

    @Override
    protected void afterLogging(final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
            final ServiceContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, String logContent, Throwable ioEx) throws Exception {
    }

    protected void nak(ServiceContext context, HttpResponseStatus httpResponseStatus, int appErrorCode, String errorMessage) {
        // 1. convert to JSON
        Error e = new Error(appErrorCode, null, errorMessage, null);
        // 2. build JSON context with same app error code, and keep the default INFO log level.
        context.status(httpResponseStatus).error(e);
    }

    /**
     * Build negative acknowledgement context with exception at ERROR level when
     * ex is not null
     *
     * @param context
     * @param httpResponseStatus
     * @param appErrorCode
     * @param errorMessage
     * @param ex
     */
    protected void nakError(ServiceContext context, HttpResponseStatus httpResponseStatus, int appErrorCode, String errorMessage, Throwable ex) {
        // 1. convert to JSON
        //Error e = new ServiceError(appErrorCode, null, errorMessage, ex);
        Error e = new Error(appErrorCode, null, errorMessage, ex);
        // 2. build JSON context with same app error code and exception, and Level.ERROR is used as the default log level when exception is not null, 
        // the log level will be set to INFO once the exception is null.
        context.status(httpResponseStatus).error(e);
    }

    /**
     * Build negative acknowledgement context with exception at FATAL level, no
     * matter ex is null or not
     *
     * @param context
     * @param httpResponseStatus
     * @param appErrorCode
     * @param errorMessage
     * @param ex
     * @param emailTo
     * @param content
     */
    protected void nakFatal(ServiceContext context, HttpResponseStatus httpResponseStatus, int appErrorCode, String errorMessage, Throwable ex, Collection<String> emailTo, String content) {
        // 1. build JSON context with same app error code and exception
        nakError(context, httpResponseStatus, appErrorCode, errorMessage, ex);
        // 2. set log level to FATAL
        context.level(Level.FATAL);
        // 3. send sendAlertAsync
        if (po != null) {
            // build email content
            String briefContent = "caller=" + context.callerId() + ", request#" + context.hit() + ": " + content;
            po.sendAlertAsync(emailTo, errorMessage, briefContent, ex, true);
        }
    }
}
