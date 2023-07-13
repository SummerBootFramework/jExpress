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
package org.summerboot.jexpress.security.auth;

import io.grpc.Context;
import io.jsonwebtoken.JwtBuilder;
import io.netty.handler.codec.http.HttpHeaders;
import javax.naming.NamingException;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;
import org.summerboot.jexpress.nio.server.RequestProcessor;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface Authenticator {

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
    String signJWT(String username, String pwd, Object metaData, int validForMinutes, final ServiceContext context) throws NamingException;

    /**
     * Convert Caller to auth token, override this method to implement
     * customized token format
     *
     * @param caller
     * @return formatted auth token builder
     */
    JwtBuilder toJwt(Caller caller);

    /**
     * Success HTTP Status: 200 OK
     *
     * @param <T>
     * @param httpRequestHeaders contains Authorization = Bearer + JWT
     * @param cache
     * @param errorCode
     * @param context
     * @return Caller
     */
    <T extends Caller> T verifyToken(HttpHeaders httpRequestHeaders, AuthTokenCache cache, Integer errorCode, final ServiceContext context);

    /**
     *
     * @param <T>
     * @param authToken
     * @param cache
     * @param errorCode
     * @param context
     * @return Caller
     */
    <T extends Caller> T verifyToken(String authToken, AuthTokenCache cache, Integer errorCode, final ServiceContext context);

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
    boolean customizedAuthorizationCheck(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, ServiceContext context) throws Exception;

    /**
     * Success HTTP Status: 204 No Content
     *
     * @param httpRequestHeaders contains Authorization = Bearer + JWT
     * @param cache
     * @param context
     */
    void logoutToken(HttpHeaders httpRequestHeaders, AuthTokenCache cache, final ServiceContext context);

    /**
     * Success HTTP Status: 204 No Content
     *
     * @param authToken
     * @param cache
     * @param context
     */
    void logoutToken(String authToken, AuthTokenCache cache, final ServiceContext context);
}
