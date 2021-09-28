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
package org.summerframework.nio.server.domain;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public class ServiceRequest {

    private final ChannelHandlerContext channelHandlerCtx;
    private final HttpHeaders httpHeaders;
    private final String httpRequestPath;
    private final Map<String, List<String>> queryParams;
    private final String httpPostRequestBody;
    private Map<String, String> pathParams;
    private Map<String, String> matrixParams;

    public ServiceRequest(ChannelHandlerContext channelHandlerCtx, HttpHeaders httpHeaders, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody) {
        this.channelHandlerCtx = channelHandlerCtx;
        this.httpHeaders = httpHeaders;
        this.httpRequestPath = httpRequestPath;
        this.queryParams = queryParams;
        this.httpPostRequestBody = httpPostRequestBody;
    }

    public ChannelHandlerContext getChannelHandlerCtx() {
        return channelHandlerCtx;
    }

    public HttpHeaders getHttpHeaders() {
        return httpHeaders;
    }

    public String getHttpRequestPath() {
        return httpRequestPath;
    }

    public String getHttpPostRequestBody() {
        return httpPostRequestBody;
    }

    public void addPathParam(String pathParamName, String value) {
        if (pathParams == null) {
            pathParams = new HashMap<>();
        }
        pathParams.put(pathParamName, value);
    }

    public String getPathParam(String key) {
        if (pathParams == null) {
            return null;
        }
        return pathParams.get(key);
    }

    public void addMatrixParam(String matrixParamName, String value) {
        if (matrixParams == null) {
            matrixParams = new HashMap<>();
        }
        matrixParams.put(matrixParamName, value);
    }

    public String getMatrixParam(String key) {
        if (matrixParams == null) {
            return null;
        }
        return matrixParams.get(key);
    }

    public String getHeaderParam(String key) {
        if (httpHeaders == null) {
            return null;
        }
        return httpHeaders.get(key);
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    public String getQueryParam(String key) {
        return getParam(queryParams, key, null, 0);
    }

    public String getQueryParam(String key, final ServiceResponse response, int errorCode) {
        return getParam(queryParams, key, response, errorCode);
    }

    //Form Parameter
    public String getFormParam(String key) {
        return getFormParam(key, null, 0);
    }

    private QueryStringDecoder qd = null;
    private Map<String, List<String>> pms = null;

    public String getFormParam(String key, final ServiceResponse response, int errorCode) {
        if (qd == null) {
            qd = new QueryStringDecoder(httpPostRequestBody, StandardCharsets.UTF_8, false);
        }
        if (pms == null) {
            pms = qd.parameters();
        }
        return getParam(pms, key);
    }

    private String getParam(Map<String, List<String>> pms, String key) {
        return getParam(pms, key, null, 0);
    }

    private String getParam(Map<String, List<String>> pms, String key, final ServiceResponse response, int errorCode) {
        String value = null;
        if (pms != null && !pms.isEmpty()) {
            List<String> vs = pms.get(key);
            if (vs != null && !vs.isEmpty()) {
                value = vs.get(0);
                value = StringUtils.isBlank(value) ? null : value;
            }
        }
        if (value == null && response != null) {
            Error e = new Error(errorCode, null, key + " is required", null);
            response.status(HttpResponseStatus.BAD_REQUEST).error(e);
        }
        return value;
    }

}
