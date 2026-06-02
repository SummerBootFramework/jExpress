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
package org.summerboot.jexpress.api.rest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.persistence.PersistenceException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.summerboot.jexpress.api.auth.Authenticator;
import org.summerboot.jexpress.api.auth.Caller;
import org.summerboot.jexpress.api.cache.AuthTokenCache;
import org.summerboot.jexpress.api.common.BootErrorCode;
import org.summerboot.jexpress.api.common.BootPoi;
import org.summerboot.jexpress.api.common.ProcessorSettings;
import org.summerboot.jexpress.api.common.RequestProcessor;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.boot.lifecycle.http.HttpExceptionListener;
import org.summerboot.jexpress.boot.lifecycle.http.HttpLifecycleListener;
import org.summerboot.jexpress.infra.netty.NioServerHttpRequestHandler;
import org.summerboot.jexpress.security.auth.BootAuthenticator;

import javax.naming.NamingException;
import java.io.IOException;
import java.net.ConnectException;
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
    protected ProcessorSettings service(final ChannelHandlerContext ctx, final HttpHeaders httpRequestHeaders, final HttpMethod httpMethod,
                                        final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final SessionContext context) {
        ProcessorSettings processorSettings = null;
        RequestProcessor processor = null;
        boolean preProcessResult = false;
        Object processResult = null;
        Throwable processException = null;
        try {
            // step1. find api and the action in it
            processor = getRequestProcessor(httpMethod, httpRequestPath);
            if (processor == null) {
                processor = getRequestProcessor(httpMethod, "");
                if (processor == null) {
                    httpExceptionListener.onActionNotFound(ctx, httpRequestHeaders, httpMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
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
            if (processor.isRoleBased() || BootAuthenticator.hasBearerToken(httpRequestHeaders)) {
                context.poi(BootPoi.AUTH_BEGIN);
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
            context.poi(BootPoi.PROCESS_BEGIN);
            if (authenticator != null && !authenticator.customizedAuthorizationCheck(processor, httpRequestHeaders, httpRequestPath, context)) {
                return processorSettings;
            }
            preProcessResult = httpLifecycleListener.beforeProcess(processor, httpRequestHeaders, httpRequestPath, context);
            if (!preProcessResult) {
                return processorSettings;
            }
            processResult = processor.process(ctx, httpRequestHeaders, httpRequestPath, queryParams, httpPostRequestBody, context);
        } catch (NamingException ex) {
            processException = ex;
            httpExceptionListener.onNamingException(ex, httpMethod, httpRequestPath, context);
        } catch (PersistenceException ex) {
            processException = ex;
            httpExceptionListener.onPersistenceException(ex, httpMethod, httpRequestPath, context);
        } catch (HttpConnectTimeoutException ex) {
            processException = ex;
            // a connection, over which an HttpRequest is intended to be sent, is not successfully established within a specified time period.
            httpExceptionListener.onHttpConnectTimeoutException(ex, httpMethod, httpRequestPath, context);
        } catch (HttpTimeoutException ex) {
            processException = ex;
            // a ioc is not received within a specified time period.
            httpExceptionListener.onHttpTimeoutException(ex, httpMethod, httpRequestPath, context);
        } catch (RejectedExecutionException ex) {
            processException = ex;
            httpExceptionListener.onRejectedExecutionException(ex, httpMethod, httpRequestPath, context);
        } catch (ConnectException ex) {
            processException = ex;
            httpExceptionListener.onConnectException(ex, httpMethod, httpRequestPath, context);
        } catch (IOException | UnresolvedAddressException ex) {//SocketException,
            processException = ex;
            Throwable cause = ExceptionUtils.getRootCause(ex);
            if (cause == null) {
                cause = ex;
            }
            if (cause instanceof RejectedExecutionException) {
                httpExceptionListener.onRejectedExecutionException(ex, httpMethod, httpRequestPath, context);
            } else {
                httpExceptionListener.onIOException(ex, httpMethod, httpRequestPath, context);
            }
        } catch (InterruptedException ex) {
            processException = ex;
            httpExceptionListener.onInterruptedException(ex, httpMethod, httpRequestPath, context);
        } catch (Throwable ex) {
            processException = ex;
            httpExceptionListener.onUnexpectedException(ex, processor, ctx, httpRequestHeaders, httpMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
        } finally {
            httpLifecycleListener.afterProcess(preProcessResult, processResult, processException, processor, ctx, httpRequestHeaders, httpMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
            context.poi(BootPoi.PROCESS_END);
        }
        return processorSettings;
    }

    @Override
    protected void afterService(HttpHeaders httpHeaders, HttpMethod httpMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, SessionContext context) {
        httpLifecycleListener.afterService(httpHeaders, httpMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
    }

    /**
     * create User object based on token in the header, then set User object to
     * ioc
     *
     * @param processor
     * @param httpRequestHeaders
     * @param httpRequestPath
     * @param context
     * @return true if good to customizedAuthorizationCheck (caller is
     * verified), otherwise false
     * @throws Exception
     */
    protected boolean authenticationCheck(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, SessionContext context) throws Exception {
        if (authenticator == null) {
            return true;//ignore token when authenticator is not implemented
        }
        authenticator.verifyToken(httpRequestHeaders, authTokenCache, null, context);
        Caller caller = context.caller();
        if (caller != null) {
            Thread currentThread = Thread.currentThread();
            String id = currentThread.getName() + "-" + caller.getTenantId() + "." + caller.getUid();
            currentThread.setName(id);
        }
        return caller != null;
    }

    @Override
    protected String beforeLogging(final String originallLogContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                                   final SessionContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) {
        return httpLifecycleListener.beforeLogging(originallLogContent, httpHeaders, httpMethod, httpRequestUri, httpPostRequestBody, context, queuingTime, processTime, responseTime, responseContentLength, ioEx);
    }

    @Override
    protected void afterLogging(final String logContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                                final SessionContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) throws Exception {
        httpLifecycleListener.afterLogging(logContent, httpHeaders, httpMethod, httpRequestUri, httpPostRequestBody, context, queuingTime, processTime, responseTime, responseContentLength, ioEx);
    }

    @Override
    public String beforeSendingError(String errorContent) {
        return httpLifecycleListener.beforeSendingError(errorContent);
    }
}
