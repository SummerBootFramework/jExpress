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
package org.summerboot.jexpress.boot.lifecycle;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import jakarta.persistence.PersistenceException;
import org.summerboot.jexpress.core.session.SessionContext;
import org.summerboot.jexpress.web.netty.handler.RequestProcessor;

import javax.naming.NamingException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 1.0
 */
public interface HttpExceptionListener {

    void onActionNotFound(final ChannelHandlerContext ctx, final HttpHeaders httpRequestHeaders, final HttpMethod httpMethod,
                          final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final SessionContext context);

    void onNamingException(NamingException ex, final HttpMethod httpMethod, final String httpRequestPath, final SessionContext context);

    void onPersistenceException(PersistenceException ex, final HttpMethod httpMethod, final String httpRequestPath, final SessionContext context);

    /**
     * Happens when a connection, over which an HttpRequest is intended to be
     * sent, is not successfully established within a specified time period.
     *
     * @param ex
     * @param httpMethod
     * @param httpRequestPath
     * @param context
     */
    void onHttpConnectTimeoutException(HttpConnectTimeoutException ex, final HttpMethod httpMethod, final String httpRequestPath, final SessionContext context);

    /**
     * Happens when a context is not received within a specified time period.
     *
     * @param ex
     * @param httpMethod
     * @param httpRequestPath
     * @param context
     */
    void onHttpTimeoutException(HttpTimeoutException ex, final HttpMethod httpMethod, final String httpRequestPath, final SessionContext context);

    void onRejectedExecutionException(Throwable ex, final HttpMethod httpMethod, final String httpRequestPath, final SessionContext context);

    void onConnectException(Throwable ex, HttpMethod httpMethod, String httpRequestPath, SessionContext context);

    void onIOException(Throwable ex, final HttpMethod httpMethod, final String httpRequestPath, final SessionContext context);

    void onInterruptedException(InterruptedException ex, final HttpMethod httpMethod, final String httpRequestPath, final SessionContext context);

    void onUnexpectedException(Throwable ex, RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httpMethod, String httpRequestPath, Map<String, List<String>> queryParams,
                               String httpPostRequestBody, SessionContext context);

}
