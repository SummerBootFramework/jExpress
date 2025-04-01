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
import io.grpc.Grpc;
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
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;
import org.summerboot.jexpress.nio.grpc.BearerAuthCredential;
import org.summerboot.jexpress.nio.grpc.ContextualizedServerCallListenerEx;
import org.summerboot.jexpress.nio.grpc.GRPCServerConfig;
import org.summerboot.jexpress.nio.server.RequestProcessor;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.security.JwtUtil;
import org.summerboot.jexpress.util.FormatterUtil;
import org.summerboot.jexpress.util.GeoIpUtil;

import javax.naming.NamingException;
import java.net.SocketAddress;
import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
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

        context.caller(caller);
        return signJWT(caller, validForMinutes, context);
    }

    @Override
    public String signJWT(Caller caller, int validForMinutes, final ServiceContext context) {
        if (caller == null) {
            context.status(HttpResponseStatus.UNAUTHORIZED);
            return null;
        }

        //3. format JWT and set token TTL from caller
        JwtBuilder builder = toJwt(caller, context.txId());

        //5. create JWT
        Key signingKey = AuthConfig.cfg.getJwtSigningKey();
        if (signingKey == null) {
            throw new UnsupportedOperationException(ERROR_NO_CFG);
        }
        // override caller TTL if validForMinutes is greater than 0
        Duration tokenTTL = Duration.ofMinutes(validForMinutes);
        String token = JwtUtil.createJWT(signingKey, builder, tokenTTL);
        if (authenticatorListener != null) {
            authenticatorListener.onLoginSuccess(caller.getUid(), token);
        }
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
     * @param txId
     * @return formatted auth token builder
     */
    @Override
    public JwtBuilder toJwt(Caller caller, String txId) {
        String jti = caller.getTenantId() + "-" + caller.getId() + "@" + txId; // tenantId-userId@txId
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
//        if (caller.getId() != null) {
//            builder.claim(KEY_CALLERID, caller.getId());
//        }
//        if (caller.getTenantId() != null) {
//            builder.claim(KEY_TENANTID, caller.getTenantId());
//        }
        if (StringUtils.isNotBlank(caller.getTenantName())) {
            builder.claim(KEY_TENANTNAME, caller.getTenantName());
        }
        Set<Map.Entry<String, Object>> callerCustomizedFields = caller.customizedFields();
        if (callerCustomizedFields != null) {
            for (Map.Entry<String, Object> entry : callerCustomizedFields) {
                if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                builder.claim(entry.getKey(), entry.getValue());
            }
        }

        //JwtBuilder builder = Jwts.builder().setClaims(claims);
        Long tokenTtlSec = caller.getTokenTtlSec();
        if (tokenTtlSec != null) {
            Duration tokenTTL = Duration.ofSeconds(tokenTtlSec);
            JwtUtil.setJwtExpireTime(builder, tokenTTL);
        }
        return builder;
    }

    protected Claims parseJWT(String jwt) {
        JwtParser jwtParser = AuthConfig.cfg.getJwtParser();
        if (jwtParser == null) {
            throw new UnsupportedOperationException(ERROR_NO_CFG);
        }
        return JwtUtil.parseJWT(jwtParser, jwt).getPayload();
    }

    //    private static final String KEY_CALLERID = "callerId";
//    private static final String KEY_TENANTID = "tenantId";
    private static final String KEY_TENANTNAME = "tenantName";

    /**
     * Convert Caller back from auth token, override this method to implement
     * customized token format
     *
     * @param claims
     * @return Caller
     */
    protected Caller fromJwt(Claims claims) {
        //String issuer = claims.getIssuer();
        String userName = claims.getSubject();
        Set<String> audience = claims.getAudience();
//        Long userId = claims.get(KEY_CALLERID, Long.class);
//        Long tenantId = claims.get(KEY_TENANTID, Long.class);
        String jti = claims.getId(); // tenantId-userId@txId
        Long tenantId = 0L, userId = 0L;
        // parse jti into tenantId and userId
        try {
            String[] arr0 = FormatterUtil.parseDsv(jti, "-");
            if (arr0 != null && arr0.length > 1) {
                tenantId = Long.parseLong(arr0[0]);
                String[] arr1 = FormatterUtil.parseDsv(arr0[1], "@");
                userId = Long.parseLong(arr1[0]);
            }
        } catch (Exception ex) {
            //ignore
        }
        String tenantName = claims.get(KEY_TENANTNAME, String.class);

        User caller = new User(tenantId, tenantName, userId, userName);

        if (audience != null) {
            for (String group : audience) {
                caller.addGroup(group);
                if (BootConstant.CFG_JWT_AUD_AS_CSV && audience.size() == 1) {
                    String[] arr = FormatterUtil.parseCsv(group);
                    if (arr != null) {
                        for (String g : arr) {
                            if (StringUtils.isNotBlank(g)) {
                                caller.addGroup(g);
                            }
                        }
                    }
                }
            }
        }

        Set<String> keys = claims.keySet();
        if (keys != null) {
            for (String key : keys) {
                Object v = claims.get(key);
                caller.setCustomizedField(key, v);
            }
        }
        caller.removeCustomizedField(Claims.AUDIENCE);
        caller.removeCustomizedField(Claims.EXPIRATION);
        caller.removeCustomizedField(Claims.ID);
        caller.removeCustomizedField(Claims.ISSUED_AT);
        caller.removeCustomizedField(Claims.ISSUER);
        caller.removeCustomizedField(Claims.NOT_BEFORE);
        caller.removeCustomizedField(Claims.SUBJECT);
//        caller.removeCustomizedField(KEY_CALLERID);
//        caller.removeCustomizedField(KEY_TENANTID);
        caller.removeCustomizedField(KEY_TENANTNAME);

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
        Status status = null;
        Context ctx = Context.current();
        long start = System.currentTimeMillis();
        Caller caller = null;
        String jti = null;
        try {
            GRPCServerConfig gRPCCfg = GRPCServerConfig.cfg;
            SocketAddress remoteAddr = serverCall.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            String error = GeoIpUtil.callerAddressFilter(remoteAddr, gRPCCfg.getCallerAddressFilterWhitelist(), gRPCCfg.getCallerAddressFilterBlacklist(), gRPCCfg.getCallerAddressFilterRegexPrefix(), gRPCCfg.getCallerAddressFilterOption());
            if (error != null) {
                //Err err = new Err(BootErrorCode.AUTH_INVALID_IP, null, null, null, "Invalid IP address: " + error);
                status = Status.UNAUTHENTICATED.withDescription(ERROR + "Invalid IP address: " + error);
            } else {
                ctx = ctx.withValue(GrpcCallerAddr, remoteAddr);
                String headerValueAuthorization = metadata.get(BearerAuthCredential.AUTHORIZATION_METADATA_KEY);
                if (headerValueAuthorization == null) {
                    //status = Status.UNAUTHENTICATED.withDescription(ERROR + "Authorization header is missing");
                    //return Contexts.interceptCall(Context.current(), serverCall, metadata, serverCallHandler);
                    status = null;
                } else if (!headerValueAuthorization.startsWith(BearerAuthCredential.BEARER_TYPE)) {
                    status = Status.UNAUTHENTICATED.withDescription(ERROR + "Unknown authorization type, non " + BearerAuthCredential.BEARER_TYPE + " token provided");
                } else {
                    String jwt = headerValueAuthorization.substring(BearerAuthCredential.BEARER_TYPE.length()).trim();
                    ServiceContext context = ServiceContext.build(0);
                    caller = verifyToken(jwt, authTokenCache, null, context);
                    if (caller == null) {
                        String desc = context.error().getErrors().get(0).getErrorDesc();
                        if (StringUtils.isBlank(desc)) {
                            desc = context.status() + "";
                        }
                        status = Status.UNAUTHENTICATED.withDescription(ERROR + desc);
                        //throw new StatusRuntimeException(status);
                    } else {
                        ctx = ctx.withValue(GrpcCaller, caller);
                        jti = context.callerId();
                        if (jti != null) {
                            ctx = ctx.withValue(GrpcCallerId, jti);
                        }
                        //return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
                        status = null;
                    }
                }
            }
        } catch (Throwable ex) {
            status = Status.UNAUTHENTICATED.withDescription(ERROR + ex.getMessage()).withCause(ex);
        }

        if (status == null) {
            // success process
            //return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
            return ContextualizedServerCallListenerEx.interceptCall(start, caller, jti, ctx, serverCall, metadata, serverCallHandler);
        }
        // error process
        serverCall.close(status, metadata);
        return new ServerCall.Listener<ReqT>() {
        };
    }


}

