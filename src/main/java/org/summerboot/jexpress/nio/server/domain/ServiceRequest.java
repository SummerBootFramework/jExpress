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
package org.summerboot.jexpress.nio.server.domain;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.util.FormatterUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ServiceRequest {

    protected final ChannelHandlerContext channelHandlerCtx;
    protected final HttpHeaders httpHeaders;
    protected final String httpRequestPath;
    protected final Map<String, List<String>> queryParams;
    protected final String httpPostRequestBody;
    protected Map<String, String> pathParams;
    protected Map<String, String> matrixParams;

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

    public String getQueryParam(String key, final ServiceContext context, int errorCode) {
        return getParam(queryParams, key, context, errorCode);
    }

    //Form Parameter
    protected Map<String, String> formParams = null;

    public String getFormParam(String key) {
        if (formParams == null) {
            formParams = new LinkedHashMap();
            FormatterUtil.parseFormParam(httpPostRequestBody, formParams);
        }
        return formParams.get(key);
    }

    protected String getParam(Map<String, List<String>> pms, String key, final ServiceContext context, int errorCode) {
        String value = null;
        if (pms != null && !pms.isEmpty()) {
            List<String> vs = pms.get(key);
            if (vs != null && !vs.isEmpty()) {
                value = vs.get(0);
                value = StringUtils.isBlank(value) ? null : value;
            }
        }
        if (value == null && context != null) {
            Err e = new Err(errorCode, null, null, null, key + " is required");
            context.status(HttpResponseStatus.BAD_REQUEST).error(e);
        }
        return value;
    }

}
