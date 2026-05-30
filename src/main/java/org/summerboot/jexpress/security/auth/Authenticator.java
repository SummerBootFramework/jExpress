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
package org.summerboot.jexpress.security.auth;

import io.grpc.Context;
import io.jsonwebtoken.JwtBuilder;
import io.netty.handler.codec.http.HttpHeaders;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.infra.netty.RequestProcessor;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;

import javax.naming.NamingException;
import java.net.SocketAddress;

/**
 * @param <T>
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface Authenticator<T> {

    /**
     * caller remote address
     */
    Context.Key<SocketAddress> GrpcCallerAddr = Context.key("addr");

    /**
     * gRPC JWT verification result
     */
    Context.Key<String> GrpcCallerId = Context.key("uid");

    /**
     * gRPC JWT verification result
     */
    Context.Key<Caller> GrpcCaller = Context.key("caller");

    /**
     * Success HTTP Status: 201 Created
     *
     * @param username
     * @param pwd
     * @param metaData
     * @param validForMinutes
     * @param context
     * @return JWT
     * @throws javax.naming.NamingException
     */
    String signJWT(String username, String pwd, T metaData, int validForMinutes, final SessionContext context) throws NamingException;


    /**
     * Success HTTP Status: 201 Created
     *
     * @param caller
     * @param validForMinutes
     * @param context
     * @return
     */
    String signJWT(Caller caller, int validForMinutes, final SessionContext context);

    /**
     * Convert Caller to auth token, override this method to implement
     * customized token format
     *
     * @param caller
     * @param txId
     * @return formatted auth token builder
     */
    JwtBuilder toJwt(Caller caller, String txId);

    /**
     * Success HTTP Status: 200 OK
     *
     * @param httpRequestHeaders contains Authorization = Bearer + JWT
     * @param cache
     * @param errorCode
     * @param context
     * @return Caller
     */
    Caller verifyToken(HttpHeaders httpRequestHeaders, AuthTokenCache cache, Integer errorCode, final SessionContext context);

    /**
     * @param authToken
     * @param cache
     * @param errorCode
     * @param context
     * @return Caller
     */
    Caller verifyToken(String authToken, AuthTokenCache cache, Integer errorCode, final SessionContext context);

    /**
     * Extra authorization checks before processing
     *
     * @param processor
     * @param httpRequestHeaders
     * @param httpRequestPath
     * @param context
     * @return true if good to process request, otherwise false
     * @throws Exception
     */
    boolean customizedAuthorizationCheck(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, SessionContext context) throws Exception;

    /**
     * Success HTTP Status: 204 No Content
     *
     * @param httpRequestHeaders contains Authorization = Bearer + JWT
     * @param cache
     * @param context
     */
    void logoutToken(HttpHeaders httpRequestHeaders, AuthTokenCache cache, final SessionContext context);

    /**
     * Success HTTP Status: 204 No Content
     *
     * @param authToken
     * @param cache
     * @param context
     */
    void logoutToken(String authToken, AuthTokenCache cache, final SessionContext context);


    /**
     * Generate a one-time token for WebSocket authentication, the token will be stored in Redis with a short TTL (e.g., 10 seconds)
     * in production, generate a random string as one-time token, store it in redis with key "ws:token:" + oneTimeToken, value = caller (or json string),
     * and set expire time to 10 seconds. return the one-time token string to caller.
     *
     * @param wsURI   WebSocket URI
     * @param jwt     caller's JWT, can be used to verify caller's identity and generate one-time token for specific user
     * @param context contains caller info, e.g. caller.getUid() can be used to generate one-time token for specific user
     * @return (32 to 64 chars + prefix) random string as one-time token, e.g. t_f87yfs7shfash7kk7a877asdf
     */
    String oneTimeTokenAuthenticate(String wsURI, String jwt, SessionContext context);

    /**
     * in production, call redis.getdel("ws:token:" + oneTimeToken)
     *
     * @param oneTimeToken
     * @return
     */
    Caller oneTimeTokenVerifyAndDestroy(String oneTimeToken);
}
