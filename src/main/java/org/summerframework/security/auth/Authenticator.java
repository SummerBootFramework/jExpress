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
package org.summerframework.security.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.netty.handler.codec.http.HttpHeaders;
import java.io.IOException;
import javax.naming.NamingException;
import org.summerframework.boot.instrumentation.HealthInspector;
import org.summerframework.integration.cache.AuthTokenCache;
import org.summerframework.nio.server.domain.ServiceContext;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface Authenticator extends HealthInspector {

    //<T extends BootCache> void setCache(T cache);
    /**
     *
     * @param listener
     */
    void setListener(AuthenticatorListener listener);

    /**
     * Success HTTP Status: 201 Created
     *
     * @param uid
     * @param pwd
     * @param validForMinutes
     * @param context
     * @return JWT
     * @throws java.io.IOException
     * @throws javax.naming.NamingException
     */
    String authenticate(String uid, String pwd, int validForMinutes, final ServiceContext context) throws IOException, NamingException;

    JwtBuilder marshalCaller(Caller caller);

    Caller unmarshalCaller(Claims claims);

    /**
     * Retrieve token based on RFC 6750 - The OAuth 2.0 Authorization Framework
     * override this method to get customized token
     *
     * @param httpRequestHeaders
     * @return
     */
    String getBearerToken(HttpHeaders httpRequestHeaders);

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
    <T extends Caller> T verifyBearerToken(HttpHeaders httpRequestHeaders, AuthTokenCache cache, Integer errorCode, final ServiceContext context);

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
     * Success HTTP Status: 204 No Content
     *
     * @param httpRequestHeaders contains Authorization = Bearer + JWT
     * @param cache
     * @param context
     */
    void logout(HttpHeaders httpRequestHeaders, AuthTokenCache cache, final ServiceContext context);

    /**
     * Success HTTP Status: 204 No Content
     *
     * @param authToken
     * @param cache
     * @param context
     */
    void logout(String authToken, AuthTokenCache cache, final ServiceContext context);
}
