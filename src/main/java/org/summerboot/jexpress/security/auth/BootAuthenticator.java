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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;
import org.summerboot.jexpress.nio.grpc.BearerAuthCredential;
import org.summerboot.jexpress.nio.server.RequestProcessor;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.security.JwtUtil;

import javax.naming.NamingException;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.Set;

/**
 * @param <E> authenticate(T metaData)
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public abstract class BootAuthenticator<E> implements Authenticator<E>, ServerInterceptor {

    protected static final String ERROR_NO_CFG = "JWT is not configured at " + AuthConfig.cfg.getCfgFile().getAbsolutePath();

    @Inject(optional = true)
    protected AuthenticatorListener authenticatorListener;

    @Inject
    protected AuthTokenCache authTokenCache;

    /**
     * @param username
     * @param pwd
     * @param validForMinutes
     * @param context
     * @return
     * @throws NamingException
     */
    @Override
    public String signJWT(String username, String pwd, E metaData, int validForMinutes, final ServiceContext context) throws NamingException {
        //1. protect request body from being logged
        //context.logRequestBody(true);@Deprecated use @Log(requestBody = false, responseHeader = false) at @Controller method level

        //2. signJWT caller against LDAP or DB
        context.poi(BootPOI.LDAP_BEGIN);
        Caller caller = authenticate(username, pwd, (E) metaData, authenticatorListener, context);
        context.poi(BootPOI.LDAP_END);
        if (caller == null) {
            context.status(HttpResponseStatus.UNAUTHORIZED);
            return null;
        }

        // get token TTL from caller, otherwise use default
        Long tokenTtlSec = caller.getTokenTtlSec();
        Duration tokenTTL;
        if (tokenTtlSec != null) {
            tokenTTL = Duration.ofSeconds(tokenTtlSec);
        } else {
            tokenTTL = Duration.ofMinutes(validForMinutes);
        }

        //3. format JWT
        JwtBuilder builder = toJwt(caller);

        //4. create JWT
        Key signingKey = AuthConfig.cfg.getJwtSigningKey();
        if (signingKey == null) {
            throw new UnsupportedOperationException(ERROR_NO_CFG);
        }
        String token = JwtUtil.createJWT(signingKey, builder, tokenTTL);
        if (authenticatorListener != null) {
            authenticatorListener.onLoginSuccess(caller.getUid(), token);
        }
        context.caller(caller);
        return token;
    }

    /**
     * @param usename
     * @param password
     * @param metaData
     * @param listener
     * @param context
     * @return
     * @throws NamingException
     */
    abstract protected Caller authenticate(String usename, String password, E metaData, AuthenticatorListener listener, final ServiceContext context) throws NamingException;

    /**
     * Convert Caller to auth token, override this method to implement
     * customized token format
     *
     * @param caller
     * @return formatted auth token builder
     */
    @Override
    public JwtBuilder toJwt(Caller caller) {
        String jti = caller.getTenantId() + "." + caller.getId() + "_" + caller.getUid() + "_" + System.currentTimeMillis();
        String issuer = AuthConfig.cfg.getJwtIssuer();
        String userName = caller.getUid();
        Set<String> groups = caller.getGroups();
        /*String groupsCsv = groups == null || groups.size() < 1
                ? null
                : groups.stream().collect(Collectors.joining(","));
        String audience = groupsCsv;*/

        JwtBuilder builder = Jwts.builder();
        builder.id(jti)
                .issuer(issuer)
                .subject(userName)
                .audience().add(groups);
        if (caller.getId() != null) {
            builder.claim("callerId", caller.getId());
        }
        if (caller.getTenantId() != null) {
            builder.claim("tenantId", caller.getTenantId());
        }
        if (caller.getTenantName() != null) {
            builder.claim("tenantName", caller.getTenantName());
        }
        Set<String> keys = caller.propKeySet();
        if (keys != null) {
            for (String key : keys) {
                Object v = caller.getProp(key, Object.class);
                builder.claim(key, v);
            }
        }

        //JwtBuilder builder = Jwts.builder().setClaims(claims);
        return builder;
    }

    protected Claims parseJWT(String jwt) {
        JwtParser jwtParser = AuthConfig.cfg.getJwtParser();
        if (jwtParser == null) {
            throw new UnsupportedOperationException(ERROR_NO_CFG);
        }
        return JwtUtil.parseJWT(jwtParser, jwt).getPayload();
    }

    /**
     * Convert Caller back from auth token, override this method to implement
     * customized token format
     *
     * @param claims
     * @return Caller
     */
    protected Caller fromJwt(Claims claims) {
        //String jti = claims.getId();
        //String issuer = claims.getIssuer();
        String userName = claims.getSubject();
        Set<String> audience = claims.getAudience();
        Long userId = claims.get("callerId", Long.class);
        Long tenantId = claims.get("tenantId", Long.class);
        String tenantName = claims.get("tenantName", String.class);

        User caller = new User(tenantId, tenantName, userId, userName);

        if (audience != null) {
            for (String group : audience) {
                caller.addGroup(group);
            }
        }

        Set<String> keys = claims.keySet();
        if (keys != null) {
            for (String key : keys) {
                Object v = claims.get(key);
                caller.putProp(key, v);
            }
        }
        caller.remove(Claims.AUDIENCE);
        caller.remove(Claims.EXPIRATION);
        caller.remove(Claims.ID);
        caller.remove(Claims.ISSUED_AT);
        caller.remove(Claims.ISSUER);
        caller.remove(Claims.NOT_BEFORE);
        caller.remove(Claims.SUBJECT);
        caller.remove("callerId");
        caller.remove("tenantId");
        caller.remove("tenantName");

        return caller;
    }

    /**
     * Retrieve token based on RFC 6750 - The OAuth 2.0 Authorization Framework
     * override this method to get customized token
     *
     * @param httpRequestHeaders
     * @return
     */
    protected String getBearerToken(HttpHeaders httpRequestHeaders) {
        String authHeaderValue = httpRequestHeaders.get(HttpHeaderNames.AUTHORIZATION);
        return getBearerToken(authHeaderValue);
    }

    /**
     * Retrieve token based on RFC 6750 - The OAuth 2.0 Authorization Framework
     * override this method to get customized token
     *
     * @param authHeaderValue "Bearer jwt"
     * @return
     */
    protected String getBearerToken(String authHeaderValue) {
        // return authHeaderValue.substring(BearerAuthCredential.BEARER_TYPE.length()).trim();
        if (StringUtils.isBlank(authHeaderValue) || !authHeaderValue.startsWith("Bearer ")) {
            return null;
        }
        String[] a = authHeaderValue.split(" ");
        if (a.length < 2) {
            return null;
        }
        authHeaderValue = a[1];
        if (StringUtils.isBlank(authHeaderValue)) {
            return null;
        }
        return authHeaderValue;
    }

    /**
     * @param httpRequestHeaders
     * @param cache
     * @param errorCode
     * @param context
     * @return
     */
    @Override
    public Caller verifyToken(HttpHeaders httpRequestHeaders, AuthTokenCache cache, Integer errorCode, ServiceContext context) {
        String authToken = getBearerToken(httpRequestHeaders);
        return verifyToken(authToken, cache, errorCode, context);
    }

    /**
     * @param authToken
     * @param cache
     * @param errorCode
     * @param context
     * @return
     */
    @Override
    public Caller verifyToken(String authToken, AuthTokenCache cache, Integer errorCode, ServiceContext context) {
        errorCode = errorCode == null ? overrideVerifyTokenErrorCode() : errorCode;
        Caller caller = null;
        if (authToken == null) {
            Err e = new Err(errorCode != null ? errorCode : BootErrorCode.AUTH_REQUIRE_TOKEN, null, null, null, "Missing AuthToken");
            context.error(e).status(HttpResponseStatus.UNAUTHORIZED);
        } else {
            try {
                Claims claims = parseJWT(authToken);
                String jti = claims.getId();
                context.callerId(jti);
                if (cache == null) {
                    cache = authTokenCache;
                }
                if (cache != null && cache.isBlacklist(jti)) {// because jti is used as blacklist key in logoutToken
                    Err e = new Err(errorCode != null ? errorCode : BootErrorCode.AUTH_EXPIRED_TOKEN, null, null, null, "AuthToken has been logout");
                    context.error(e).status(HttpResponseStatus.UNAUTHORIZED);
                } else {
                    caller = fromJwt(claims);
                }
            } catch (ExpiredJwtException ex) {
                Err e = new Err(errorCode != null ? errorCode : BootErrorCode.AUTH_EXPIRED_TOKEN, null, null, null, "Expired AuthToken: " + ex);
                context.error(e).status(HttpResponseStatus.UNAUTHORIZED);
            } catch (JwtException ex) {
                Err e = new Err(errorCode != null ? errorCode : BootErrorCode.AUTH_INVALID_TOKEN, null, null, null, "Invalid AuthToken: " + ex);
                context.error(e).status(HttpResponseStatus.UNAUTHORIZED);
            }
        }
        context.caller(caller);
        return caller;
    }

    @Override
    public boolean customizedAuthorizationCheck(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, ServiceContext context) throws Exception {
        return true;
    }

    protected Integer overrideVerifyTokenErrorCode() {
        return null;
    }

    /**
     * @param httpRequestHeaders
     * @param cache
     * @param context
     */
    @Override
    public void logoutToken(HttpHeaders httpRequestHeaders, AuthTokenCache cache, ServiceContext context) {
        String authToken = getBearerToken(httpRequestHeaders);
        logoutToken(authToken, cache, context);
    }

    /**
     * @param authToken
     * @param cache
     * @param context
     */
    @Override
    public void logoutToken(String authToken, AuthTokenCache cache, ServiceContext context) {
        try {
            Claims claims = parseJWT(authToken);
            String jti = claims.getId();
            String uid = claims.getSubject();
            Date exp = claims.getExpiration();
            long expireInMilliseconds = exp.getTime() - System.currentTimeMillis();
            if (cache == null) {
                cache = authTokenCache;
            }
            if (cache != null) {
                cache.blacklist(jti, authToken, expireInMilliseconds);
            }
            if (authenticatorListener != null) {
                authenticatorListener.onLogout(claims, authToken, expireInMilliseconds);
            }
        } catch (ExpiredJwtException ex) {
            //ignore
        } catch (JwtException ex) {
            context.status(HttpResponseStatus.FORBIDDEN);
            return;
        }
        context.status(HttpResponseStatus.NO_CONTENT);
    }

    protected static final String ERROR = "Bearer Authorization Failed: ";

    /**
     * gRPC JWT verification
     *
     * @param <ReqT>
     * @param <RespT>
     * @param serverCall
     * @param metadata
     * @param serverCallHandler
     * @return
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        Status status;
        String headerValueAuthorization = metadata.get(BearerAuthCredential.AUTHORIZATION_METADATA_KEY);
        if (headerValueAuthorization == null) {
            //status = Status.UNAUTHENTICATED.withDescription(ERROR + "Authorization header is missing");            
            return Contexts.interceptCall(Context.current(), serverCall, metadata, serverCallHandler);
        } else if (!headerValueAuthorization.startsWith(BearerAuthCredential.BEARER_TYPE)) {
            status = Status.INVALID_ARGUMENT.withDescription(ERROR + "Unknown authorization type, non bearer token provided");
        } else {
            try {
                String jwt = headerValueAuthorization.substring(BearerAuthCredential.BEARER_TYPE.length()).trim();
                ServiceContext context = ServiceContext.build(0);
                Caller caller = verifyToken(jwt, authTokenCache, null, context);
                if (caller == null) {
                    String desc = context.error().getErrors().get(0).getErrorDesc();
                    status = Status.INVALID_ARGUMENT.withDescription(ERROR + desc);
                    //throw new StatusRuntimeException(status);
                } else {
                    Context ctx = Context.current().withValue(GrpcCaller, caller);
                    String jti = context.callerId();
                    if (jti != null) {
                        ctx = ctx.withValue(GrpcCallerId, jti);
                    }
                    return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
                }
            } catch (Throwable ex) {
                status = Status.INVALID_ARGUMENT.withDescription(ERROR + ex.getMessage()).withCause(ex);
            }
        }

        serverCall.close(status, metadata);
        return new ServerCall.Listener<ReqT>() {
        };
    }
}
