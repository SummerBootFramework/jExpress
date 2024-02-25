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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.persistence.PersistenceException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.boot.event.HttpExceptionListener;
import org.summerboot.jexpress.boot.event.HttpLifecycleListener;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;
import org.summerboot.jexpress.nio.server.domain.ProcessorSettings;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.security.auth.Authenticator;

import javax.naming.NamingException;
import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 2.0
 */
@Singleton
@ChannelHandler.Sharable
public class BootHttpRequestHandler extends NioServerHttpRequestHandler {

    @Inject
    protected Authenticator authenticator;

    @Inject
    protected AuthTokenCache authTokenCache;

    @Inject
    protected HttpLifecycleListener httpLifecycleListener;

    @Inject
    protected HttpExceptionListener httpExceptionListener;

    @Override
    protected ProcessorSettings service(final ChannelHandlerContext ctx, final HttpHeaders httpRequestHeaders, final HttpMethod httptMethod,
                                        final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceContext context) {
        ProcessorSettings processorSettings = null;
        RequestProcessor processor = null;
        try {
            // step1. find controller and the action in it
            processor = getRequestProcessor(httptMethod, httpRequestPath);
            if (processor == null) {
                processor = getRequestProcessor(httptMethod, "");
                if (processor == null) {
                    httpExceptionListener.onActionNotFound(ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
                    return processorSettings;
                }
            }
            processorSettings = processor.getProcessorSettings();
            ProcessorSettings.LogSettings logSettings = processorSettings.getLogSettings();
            if (logSettings != null) {
                context.logRequestHeader(logSettings.isLogRequestHeader());
                context.logRequestBody(logSettings.isLogRequestBody());
                context.logResponseHeader(logSettings.isLogResponseHeader());
                context.logResponseBody(logSettings.isLogResponseBody());
            }

            // step2. caller authentication
            if (processor.isRoleBased()) {
                context.poi(BootPOI.AUTH_BEGIN);
                if (!authenticationCheck(processor, httpRequestHeaders, httpRequestPath, context)) {
                    context.status(HttpResponseStatus.UNAUTHORIZED);
                    return processorSettings;
                }
                if (!processor.authorizationCheck(ctx, httpRequestHeaders, httpRequestPath, queryParams, httpPostRequestBody, context, BootErrorCode.AUTH_NO_PERMISSION)) {
                    context.status(HttpResponseStatus.FORBIDDEN);
                    return processorSettings;
                }
            }

            // step3. serve the request, most frequently called first, will do customizedAuthorizationCheck in next step(ControllerAction.process(...))
            context.poi(BootPOI.PROCESS_BEGIN);
            if (authenticator != null && !authenticator.customizedAuthorizationCheck(processor, httpRequestHeaders, httpRequestPath, context)) {
                return processorSettings;
            }
            if (!httpLifecycleListener.beofreProcess(processor, httpRequestHeaders, httpRequestPath, context)) {
                return processorSettings;
            }
            processor.process(ctx, httpRequestHeaders, httpRequestPath, queryParams, httpPostRequestBody, context);
            //} catch (ExpiredJwtException | SignatureException | MalformedJwtException ex) {
            //    nak(context, HttpResponseStatus.UNAUTHORIZED, BootErrorCode.AUTH_INVALID_TOKEN, "Invalid JWT");
        } catch (NamingException ex) {
            httpExceptionListener.onNamingException(ex, httptMethod, httpRequestPath, context);
        } catch (PersistenceException ex) {
            httpExceptionListener.onPersistenceException(ex, httptMethod, httpRequestPath, context);
        } catch (
                HttpConnectTimeoutException ex) {// a connection, over which an HttpRequest is intended to be sent, is not successfully established within a specified time period.
            httpExceptionListener.onHttpConnectTimeoutException(ex, httptMethod, httpRequestPath, context);
        } catch (HttpTimeoutException ex) {// a context is not received within a specified time period.
            httpExceptionListener.onHttpTimeoutException(ex, httptMethod, httpRequestPath, context);
        } catch (RejectedExecutionException ex) {
            httpExceptionListener.onRejectedExecutionException(ex, httptMethod, httpRequestPath, context);
        } catch (IOException | UnresolvedAddressException ex) {//SocketException, 
            Throwable cause = ExceptionUtils.getRootCause(ex);
            if (cause == null) {
                cause = ex;
            }
            if (cause instanceof RejectedExecutionException) {
                httpExceptionListener.onRejectedExecutionException(ex, httptMethod, httpRequestPath, context);
            } else {
                httpExceptionListener.onIOException(ex, httptMethod, httpRequestPath, context);
            }
        } catch (InterruptedException ex) {
            httpExceptionListener.onInterruptedException(ex, httptMethod, httpRequestPath, context);
        } catch (Throwable ex) {
            httpExceptionListener.onUnexpectedException(ex, processor, ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
        } finally {
            httpLifecycleListener.afterProcess(processor, ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
            context.poi(BootPOI.PROCESS_END);
        }
        return processorSettings;
    }

    /**
     * create User object based on token in the header, then set User object to
     * context
     *
     * @param processor
     * @param httpRequestHeaders
     * @param httpRequestPath
     * @param context
     * @return true if good to customizedAuthorizationCheck (caller is
     * verified), otherwise false
     * @throws Exception
     */
    protected boolean authenticationCheck(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, ServiceContext context) throws Exception {
        if (authenticator == null) {
            return true;//ignore token when authenticator is not implemented
        }
        authenticator.verifyToken(httpRequestHeaders, authTokenCache, null, context);
        return context.caller() != null;
    }

    @Override
    protected String beforeLogging(final String originallLogContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                                   final ServiceContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) {
        return httpLifecycleListener.beforeLogging(originallLogContent, httpHeaders, httpMethod, httpRequestUri, httpPostRequestBody, context, queuingTime, processTime, responseTime, responseContentLength, ioEx);
    }

    @Override
    protected void afterLogging(final String logContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                                final ServiceContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) throws Exception {
        httpLifecycleListener.afterLogging(logContent, httpHeaders, httpMethod, httpRequestUri, httpPostRequestBody, context, queuingTime, processTime, responseTime, responseContentLength, ioEx);
    }

    @Override
    public String beforeSendingError(String errorContent) {
        return httpLifecycleListener.beforeSendingError(errorContent);
    }
}
