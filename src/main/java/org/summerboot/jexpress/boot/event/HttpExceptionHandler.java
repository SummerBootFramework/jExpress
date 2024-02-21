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
package org.summerboot.jexpress.boot.event;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.persistence.PersistenceException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.integration.smtp.PostOffice;
import org.summerboot.jexpress.integration.smtp.SMTPClientConfig;
import org.summerboot.jexpress.nio.server.RequestProcessor;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;

import javax.naming.AuthenticationException;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 1.0
 */
@Singleton
public class HttpExceptionHandler implements HttpExceptionListener {

    @Inject
    protected HealthInspector healthInspector;

    @Inject
    protected PostOffice po;

    @Override
    public void onActionNotFound(ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceContext context) {
        Err e = new Err(BootErrorCode.AUTH_INVALID_URL, null, null, null, "Action not found: " + httptMethod + " " + httpRequestPath);
        context.error(e).status(HttpResponseStatus.NOT_FOUND).logRequestHeader(false).logRequestBody(false);
    }

    @Override
    public void onNamingException(NamingException ex, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        if (ex instanceof AuthenticationException) {
            Err e = new Err(BootErrorCode.AUTH_INVALID_USER, null, null, null, "Authentication failed");
            context.error(e).status(HttpResponseStatus.UNAUTHORIZED);
        } else {
            Throwable cause = ExceptionUtils.getRootCause(ex);
            if (cause == null) {
                cause = ex;
            }
            if (cause instanceof IOException) {// java.net.UnknownHostException
                HealthMonitor.setHealthStatus(false, ex.toString(), healthInspector);
                nakFatal(context, HttpResponseStatus.SERVICE_UNAVAILABLE, BootErrorCode.ACCESS_ERROR_LDAP, "LDAP " + cause.getClass().getSimpleName(), ex, SMTPClientConfig.cfg.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);
            } else {
                onNamingException(ex, cause, httptMethod, httpRequestPath, context);
            }
        }
    }

    protected void onNamingException(NamingException ex, Throwable cause, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        Err e = new Err(BootErrorCode.ACCESS_ERROR_LDAP, null, null, ex, cause.toString());
        context.error(e).status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public void onPersistenceException(PersistenceException ex, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        Throwable cause = ExceptionUtils.getRootCause(ex);
        if (cause == null) {
            cause = ex;
        }
        if (cause instanceof IOException) {// java.net.ConnectException
            HealthMonitor.setHealthStatus(false, ex.toString(), healthInspector);
            nakFatal(context, HttpResponseStatus.SERVICE_UNAVAILABLE, BootErrorCode.ACCESS_ERROR_DATABASE, "DB " + cause.getClass().getSimpleName(), ex, SMTPClientConfig.cfg.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);
        } else {
            onPersistenceException(ex, cause, httptMethod, httpRequestPath, context);
        }
    }

    public void onPersistenceException(PersistenceException ex, Throwable cause, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        Err e = new Err(BootErrorCode.ACCESS_ERROR_DATABASE, null, null, ex, cause.toString());
        context.error(e).status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    public void onHttpConnectTimeoutException(HttpConnectTimeoutException ex, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        context.status(HttpResponseStatus.GATEWAY_TIMEOUT)
                .level(Level.WARN)
                .error(new Err(BootErrorCode.HTTP_CONNECTION_TIMEOUT, null, null, ex, "Http Connect Timeout: " + ex.getMessage()));
    }

    @Override
    public void onHttpTimeoutException(HttpTimeoutException ex, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        context.status(HttpResponseStatus.GATEWAY_TIMEOUT)
                .level(Level.WARN)
                .error(new Err(BootErrorCode.HTTP_REQUEST_TIMEOUT, null, null, ex, "Http Request Timeout: " + ex.getMessage()));
    }

    @Override
    public void onRejectedExecutionException(Throwable ex, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        context.status(HttpResponseStatus.SERVICE_UNAVAILABLE)
                .level(Level.WARN)
                .error(new Err(BootErrorCode.HTTPCLIENT_TOO_MANY_CONNECTIONS_REJECT, null, null, ex, "Too many request, try again later: " + ex.getMessage()));
    }

    @Override
    public void onIOException(Throwable ex, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        HealthMonitor.setHealthStatus(false, ex.toString(), healthInspector);
        nakFatal(context, HttpResponseStatus.SERVICE_UNAVAILABLE, BootErrorCode.IO_BASE, "IO issue: " + ex.getClass().getSimpleName(), ex, SMTPClientConfig.cfg.getEmailToAppSupport(), httptMethod + " " + httpRequestPath);

    }

    @Override
    public void onInterruptedException(InterruptedException ex, HttpMethod httptMethod, String httpRequestPath, ServiceContext context) {
        Thread.currentThread().interrupt();
        nakFatal(context, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.APP_INTERRUPTED, "Service Interrupted", ex, SMTPClientConfig.cfg.getEmailToDevelopment(), httptMethod + " " + httpRequestPath);
    }

    @Override
    public void onUnexpectedException(Throwable ex, RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceContext context) {
        nakFatal(context, HttpResponseStatus.INTERNAL_SERVER_ERROR, BootErrorCode.NIO_UNEXPECTED_PROCESSOR_FAILURE, "Unexpected Failure: " + ex.getClass().getSimpleName(), ex, SMTPClientConfig.cfg.getEmailToDevelopment(), httptMethod + " " + httpRequestPath);
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
        Err e = new Err(appErrorCode, null, null, ex, errorMessage);
        context.status(httpResponseStatus).level(Level.FATAL).error(e);
        // 2. send sendAlertAsync
        if (po != null) {
            // build email content
            String briefContent = "caller=" + context.callerId() + ", request#" + context.txId() + ": " + content;
            po.sendAlertAsync(emailTo, errorMessage, briefContent, ex, true);
        }
    }

}
