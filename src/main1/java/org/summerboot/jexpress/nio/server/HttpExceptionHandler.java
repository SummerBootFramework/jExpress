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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import jakarta.persistence.PersistenceException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 1.0
 */
public interface HttpExceptionHandler {

    void onActionNotFound(final ChannelHandlerContext ctx, final HttpHeaders httpRequestHeaders, final HttpMethod httptMethod,
            final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final ServiceContext context);

    void onNamingException(NamingException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context);

    void onPersistenceException(PersistenceException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context);

    /**
     * Happens when a connection, over which an HttpRequest is intended to be
     * sent, is not successfully established within a specified time period.
     *
     * @param ex
     * @param httptMethod
     * @param httpRequestPath
     * @param context
     */
    void onHttpConnectTimeoutException(HttpConnectTimeoutException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context);

    /**
     * Happens when a context is not received within a specified time period.
     *
     * @param ex
     * @param httptMethod
     * @param httpRequestPath
     * @param context
     */
    void onHttpTimeoutException(HttpTimeoutException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context);

    void onRejectedExecutionException(Throwable ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context);

    void onIOException(Throwable ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context);

    void onInterruptedException(InterruptedException ex, final HttpMethod httptMethod, final String httpRequestPath, final ServiceContext context);

    void onUnexpectedException(Throwable ex, RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath, Map<String, List<String>> queryParams,
            String httpPostRequestBody, ServiceContext context);
}
