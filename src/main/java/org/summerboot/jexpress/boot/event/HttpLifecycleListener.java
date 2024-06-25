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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.summerboot.jexpress.nio.server.RequestProcessor;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;

import java.util.List;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 * @version 1.0
 */
public interface HttpLifecycleListener {
    boolean beforeProcessPingRequest(ChannelHandlerContext ctx, String uri, long hit, HttpResponseStatus status);

    void afterSendPingResponse(ChannelHandlerContext ctx, String uri, long hit, HttpResponseStatus status);

    /**
     * step0 - do any validation checks before processing
     *
     * @param processor
     * @param httpRequestHeaders
     * @param httpRequestPath
     * @param context
     * @return true if good to process request, otherwise false
     * @throws Exception
     */
    boolean beforeProcess(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, ServiceContext context) throws Exception;

    /**
     * step1 - after process is done, before sending response to client
     *
     * @param processor
     * @param ctx
     * @param httpRequestHeaders
     * @param httptMethod
     * @param httpRequestPath
     * @param queryParams
     * @param httpPostRequestBody
     * @param context
     */
    void afterProcess(RequestProcessor processor, ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, HttpMethod httptMethod, String httpRequestPath,
                      Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceContext context);


    /**
     * step2 - after sending response to client, before starting logging
     *
     * @param httpHeaders
     * @param httpMethod
     * @param httpRequestPath
     * @param queryParams
     * @param httpPostRequestBody
     * @param context
     */
    void afterService(HttpHeaders httpHeaders, HttpMethod httpMethod, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceContext context);

    /**
     * step3
     *
     * @param errorContent
     * @return
     */
    String beforeSendingError(String errorContent);

    /**
     * step4
     *
     * @param originallLogContent
     * @param httpHeaders
     * @param httpMethod
     * @param httpRequestUri
     * @param httpPostRequestBody
     * @param context
     * @param queuingTime
     * @param processTime
     * @param responseTime
     * @param responseContentLength
     * @param ioEx
     * @return
     */
    String beforeLogging(final String originallLogContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                         final ServiceContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx);

    /**
     * step5
     *
     * @param logContent
     * @param httpHeaders
     * @param httpMethod
     * @param httpRequestUri
     * @param httpPostRequestBody
     * @param context
     * @param queuingTime
     * @param processTime
     * @param responseTime
     * @param responseContentLength
     * @param ioEx
     * @throws Exception
     */
    void afterLogging(final String logContent, final HttpHeaders httpHeaders, final HttpMethod httpMethod, final String httpRequestUri, final String httpPostRequestBody,
                      final ServiceContext context, long queuingTime, long processTime, long responseTime, long responseContentLength, Throwable ioEx) throws Exception;
}
