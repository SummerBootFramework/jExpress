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

import com.google.inject.Singleton;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.summerboot.jexpress.controller.SessionContext;
import org.summerboot.jexpress.webserver.netty.RequestProcessor;

import java.util.List;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 1.0
 */
@Singleton
public class HttpLifecycleHandler implements HttpLifecycleListener {
    @Override
    public boolean beforeProcessPingRequest(ChannelHandlerContext ctx, String uri, long hit, HttpResponseStatus status) {
        return true;
    }

    @Override
    public void afterSendPingResponse(ChannelHandlerContext ctx, String uri, long hit, HttpResponseStatus status) {
    }

    @Override
    public boolean beforeProcess(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, SessionContext context) throws Exception {
        return true;
    }

    @Override
    public void afterProcess(boolean preProcessResult, Object processResult, Throwable processException, RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, SessionContext context) {
//        if (httpRequestHeaders.contains(HttpHeaderNames.Sensitive_Header)) {
//            httpRequestHeaders.set(HttpHeaderNames.Sensitive_Header, "***");// protect Sensitive_Header from being logged
//        }
    }

    @Override
    public void afterService(HttpHeaders httpHeaders, HttpMethod httpMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, SessionContext context) {
    }

    @Override
    public String beforeSendingError(String errorContent) {
        //return FormatterUtil.protectContent(errorContent, "UnknownHostException", ":", null, " ***");
        return errorContent;
    }

    @Override
    public String beforeLogging(final String originallLogContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                                final SessionContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) {
        return originallLogContent;
    }

    @Override
    public void afterLogging(final String logContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                             final SessionContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) {
    }
}
