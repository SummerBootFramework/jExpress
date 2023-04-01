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

import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import javax.naming.NamingException;
import jakarta.persistence.PersistenceException;
import java.nio.channels.UnresolvedAddressException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;
import org.summerboot.jexpress.security.auth.Authenticator;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 2.0
 */
@Singleton
public class BootHttpRequestHandler extends NioServerHttpRequestHandler {

    public static final String BINDING_NAME = "BootHttpRequestHandler";

    @Inject
    protected Authenticator authenticator;

    @Inject
    protected NioExceptionHandler nioExceptionHandler;

    @Inject
    protected AuthTokenCache tokenCache;

    @Inject
    protected NioLifecycle nioLifecycleHandler;

    protected static SMTPClientConfig cmtpCfg = SMTPClientConfig.cfg;

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
                    nioExceptionHandler.onActionNotFound(ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
                    return;
                }
            }

            // step2. caller authentication, will do authorizationCheck in next step(ControllerAction.process(...))
            if (processor.isRoleBased()) {
                context.timestampPOI(BootPOI.AUTH_BEGIN);
                if (!authenticationCheck(processor, httpRequestHeaders, httpRequestPath, context)) {
                    context.status(HttpResponseStatus.UNAUTHORIZED);
                    return;
                }
                if (!processor.authorizationCheck(ctx, httpRequestHeaders, httpRequestPath, queryParams, httpPostRequestBody, context, BootErrorCode.AUTH_NO_PERMISSION)) {
                    context.status(HttpResponseStatus.FORBIDDEN);
                    return;
                }
            }

            // step3. serve the request, most frequently called first
            context.timestampPOI(BootPOI.PROCESS_BEGIN);
            if (authenticator != null && !authenticator.preAccessCheck(processor, httpRequestHeaders, httpRequestPath, context)) {
                return;
            }
            if (!nioLifecycleHandler.preProcess(processor, httpRequestHeaders, httpRequestPath, context)) {
                return;
            }
            processor.process(ctx, httpRequestHeaders, httpRequestPath, queryParams, httpPostRequestBody, context, BootErrorCode.NIO_WSRS_REQUEST_BAD_DATA);
            //} catch (ExpiredJwtException | SignatureException | MalformedJwtException ex) {
            //    nak(context, HttpResponseStatus.UNAUTHORIZED, BootErrorCode.AUTH_INVALID_TOKEN, "Invalid JWT");
        } catch (NamingException ex) {
            nioExceptionHandler.onNamingException(ex, httptMethod, httpRequestPath, context);
        } catch (PersistenceException ex) {
            nioExceptionHandler.onPersistenceException(ex, httptMethod, httpRequestPath, context);
        } catch (HttpConnectTimeoutException ex) {// a connection, over which an HttpRequest is intended to be sent, is not successfully established within a specified time period.
            nioExceptionHandler.onHttpConnectTimeoutException(ex, httptMethod, httpRequestPath, context);
        } catch (HttpTimeoutException ex) {// a context is not received within a specified time period.            
            nioExceptionHandler.onHttpTimeoutException(ex, httptMethod, httpRequestPath, context);
        } catch (RejectedExecutionException ex) {
            nioExceptionHandler.onRejectedExecutionException(ex, httptMethod, httpRequestPath, context);
        } catch (IOException | UnresolvedAddressException ex) {//SocketException, 
            Throwable cause = ExceptionUtils.getRootCause(ex);
            if (cause == null) {
                cause = ex;
            }
            if (cause instanceof RejectedExecutionException) {
                nioExceptionHandler.onRejectedExecutionException(ex, httptMethod, httpRequestPath, context);
            } else {
                nioExceptionHandler.onIOException(ex, httptMethod, httpRequestPath, context);
            }
        } catch (InterruptedException ex) {
            nioExceptionHandler.onInterruptedException(ex, httptMethod, httpRequestPath, context);
        } catch (Throwable ex) {
            nioExceptionHandler.onUnexpectedException(ex, processor, ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
        } finally {
            nioLifecycleHandler.afterService(processor, ctx, httpRequestHeaders, httptMethod, httpRequestPath, queryParams, httpPostRequestBody, context);
            context.timestampPOI(BootPOI.PROCESS_END);
        }
    }

    /**
     * create User object based on token in the header, then set User object to
     * context
     *
     * @param processor
     * @param httpRequestHeaders
     * @param httpRequestPath
     * @param context
     * @return true if good to preAccessCheck (caller is verified), otherwise
 false
     * @throws Exception
     */
    protected boolean authenticationCheck(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, ServiceContext context) throws Exception {
        if (authenticator == null) {
            return true;//ignore token when authenticator is not implemented
        }
        authenticator.verifyBearerToken(httpRequestHeaders, tokenCache, null, context);
        return context.caller() != null;
    }

    @Override
    protected String beforeLogging(String log) {
        return nioLifecycleHandler.beforeLogging(log);
    }

    @Override
    protected void afterLogging(final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
            final ServiceContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, String logContent, Throwable ioEx) throws Exception {
        nioLifecycleHandler.afterLogging(httpHeaders, httpMethod, httpRequestUri, httpPostRequestBody, context, queuingTime, processTime, responseTime, responseContentLength, logContent, ioEx);
    }

    @Override
    public String beforeSendingError(String errorContent) {
        return nioLifecycleHandler.beforeSendingError(errorContent);
    }
}
