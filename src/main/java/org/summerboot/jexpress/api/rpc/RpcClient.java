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
package org.summerboot.jexpress.api.rpc;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.summerboot.jexpress.api.common.SessionContext;

import java.io.IOException;
import java.net.http.HttpRequest;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface RpcClient {


    /**
     * 1a. RPC via HttpRequest.Builder in non-streaming mode, and the context will be updated with error and status if any error occurs
     *
     * @param context
     * @param httpRequestBuilder
     * @param goodStatus         expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>                successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    default <T> RpcResult<T> rpc(SessionContext context, HttpRequest.Builder httpRequestBuilder, HttpResponseStatus... goodStatus) throws IOException {
        return rpc(context, true, httpRequestBuilder, false, goodStatus);
    }

    /**
     * 1b. RPC via HttpRequest.Builder
     *
     * @param context
     * @param updateContext      if true, the context will be updated with error and status if any error occurs, otherwise the context will not be updated
     * @param httpRequestBuilder
     * @param isStreaming        response body will not be logged if isStreaming=true and no error occurs, but the response body will be logged if isStreaming=false or error occurs
     * @param goodStatus         expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>                successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, HttpRequest.Builder httpRequestBuilder, boolean isStreaming, HttpResponseStatus... goodStatus) throws IOException;

    /**
     * 2a. RPC via HttpRequest in non-streaming mode, and the context will be updated with error and status if any error occurs
     *
     * @param context
     * @param httpRequest
     * @param goodStatus  expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>         successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    default <T> RpcResult<T> rpc(SessionContext context, HttpRequest httpRequest, HttpResponseStatus... goodStatus) throws IOException {
        return rpc(context, true, httpRequest, false, goodStatus);
    }

    /**
     * 2b. RPC via HttpRequest
     *
     * @param context
     * @param updateContext if true, the context will be updated with error and status if any error occurs, otherwise the context will not be updated
     * @param httpRequest
     * @param isStreaming   response body will not be logged if isStreaming=true and no error occurs, but the response body will be logged if isStreaming=false or error occurs
     * @param goodStatus    expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>           successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, HttpRequest httpRequest, boolean isStreaming, HttpResponseStatus... goodStatus) throws IOException;


    /**
     * 3a. Reset request via RpcResult in non-streaming mode, and the context will be updated with error and status if any error occurs
     *
     * @param context
     * @param rpcResult
     * @param goodStatus expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>        successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    default <T> RpcResult<T> rpc(SessionContext context, RpcResult<T> rpcResult, HttpResponseStatus... goodStatus) throws IOException {
        return rpc(context, true, rpcResult, false, goodStatus);
    }

    /**
     * 3b. Reset request via RpcResult
     *
     * @param context
     * @param updateContext if true, the context will be updated with error and status if any error occurs, otherwise the context will not be updated
     * @param rpcResult
     * @param isStreaming   response body will not be logged if isStreaming=true and no error occurs, but the response body will be logged if isStreaming=false or error occurs
     * @param goodStatus    expected success status list, if the actual status is not in this list, it will be treated as error
     * @param <T>           successResponseClass will be used to deserialize the response body, so they can be null if the caller does not want to deserialize the response body
     * @return Non-Null RpcResult, use rpcResult.isRemoteSuccess() to check if the remote call was successful, and rpcResult.deserialize() to deserialize JSON to success/error object
     * @throws IOException
     */
    <T> RpcResult<T> rpc(SessionContext context, boolean updateContext, RpcResult<T> rpcResult, boolean isStreaming, HttpResponseStatus... goodStatus) throws IOException;
}
