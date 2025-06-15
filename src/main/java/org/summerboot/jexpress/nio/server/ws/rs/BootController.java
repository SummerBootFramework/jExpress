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
package org.summerboot.jexpress.nio.server.ws.rs;

//import co.elastic.apm.api.CaptureTransaction;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import jakarta.annotation.Nonnull;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.annotation.Deamon;
import org.summerboot.jexpress.boot.annotation.Log;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;
import org.summerboot.jexpress.nio.server.SessionContext;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.LoginVo;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.nio.server.domain.ServiceRequest;
import org.summerboot.jexpress.security.auth.AuthConfig;
import org.summerboot.jexpress.security.auth.Authenticator;
import org.summerboot.jexpress.security.auth.Caller;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
//@Controller
//@Path(CONTEXT_ROOT)
//@Consumes(MediaType.APPLICATION_JSON)
//@Produces(MediaType.APPLICATION_JSON)
@OpenAPIDefinition(//OAS v3
        info = @Info(
                title = "Default Admin API",
                version = BootConstant.VERSION,
                description = "To change to yours, just add @OpenAPIDefinition.info",
                contact = @Contact(
                        name = "summerboot.org",
                        email = ""
                )
        ),
        servers = {
                @Server(url = "https://localhost:8211", description = "Local Development server")
        }
)
@SecurityScheme(name = "BearerAuth", scheme = "bearer", type = SecuritySchemeType.HTTP, bearerFormat = "Authorization: Bearer <JWT token>")
@SecurityScheme(name = "BasicAuth", scheme = "basic", type = SecuritySchemeType.HTTP)
@SecurityScheme(name = "ApiKeyAuth", paramName = "X-API-KEY", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER)
/*@SecurityScheme(name = "OpenID", type = SecuritySchemeType.OPENIDCONNECT, openIdConnectUrl = "https://jExpress.org/.well-known/openid-configuration")
@SecurityScheme(name = "OAuth2", type = SecuritySchemeType.OAUTH2, flows = @OAuthFlows(
        authorizationCode = @OAuthFlow(
                authorizationUrl = "https://jexpress.org/oauth/authorize",
                tokenUrl = "https://jexpress.org/oauth/token",
                scopes = {
                        @OAuthScope(name = "read", description = "Read access"),
                        @OAuthScope(name = "write", description = "Write access")
                }
        )))*/
abstract public class BootController extends PingController {

    public static final String SecuritySchemeName_BearerAuth = "BearerAuth";
    public static final String SecuritySchemeName_BasicAuth = "BasicAuth";
    public static final String SecuritySchemeName_ApiKeyAuth = "ApiKeyAuth";
    public static final String SecuritySchemeName_OpenID = "OpenID";
    public static final String SecuritySchemeName_OAuth2 = "OAuth2";


    public static final String TAG_APP_ADMIN = "App Admin";
    public static final String TAG_USER_AUTH = "App Authentication";

    public static final String DESC_4xx = "This class of status code is intended for situations in which the error seems to have been caused by the client. Client normally should not retransmit the same request again.";
    public static final String DESC_400 = "Bad Request. Client should not retransmit the same request again";
    public static final String DESC_401 = "Unauthorized. The client should sign-on again, but not retransmit the same request again";
    public static final String DESC_403 = "Client has  no permission. Client should not retransmit the same request again.";
    public static final String DESC_404 = "Not Found. The client should not retransmit the same request again.";
    public static final String DESC_409 = "Conflict. Client may try again later.";
    public static final String DESC_429 = "Too Many Requests. Client may try again later";
    public static final String DESC_5xx = "This class of status code is intended for situations in which the server is aware that it has encountered an error or is otherwise incapable of performing the request. The client can continue and try again with the request without modification.";
    public static final String DESC_500 = "Internal Server Error. The client can continue and try again with the request without modification.";
    public static final String DESC_501 = "Not Implemented. The client can continue and try again with the request without modification.";
    public static final String DESC_502 = "Bad Gateway. The client can continue and try again with the request without modification.";
    public static final String DESC_503 = "Service Unavailable. The client can continue and try again with the request without modification.";
    public static final String DESC_504 = "Gateway Timeout. The client can continue and try again with the request without modification.";
    public static final String DESC_507 = "Insufficient Storage. The client should contact the system administrator. Do not try the request again.";

    @Inject
    protected AuthTokenCache authTokenCache;
    //abstract protected AuthTokenCache getAuthTokenCache();

    @Inject
    protected Authenticator auth;
    //abstract protected Authenticator getAuthenticator();

    @Operation(
            tags = {TAG_APP_ADMIN},
            summary = "Check application version",
            description = "get running application version information",
            //                        parameters = {
            //                            @Parameter(name = "", in = ParameterIn.HEADER, required = true, description = "")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "running application version"),
                    @ApiResponse(responseCode = "4xx", description = DESC_4xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    ),
                    @ApiResponse(responseCode = "5xx", description = DESC_5xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    )
            },
            security = {
                    @SecurityRequirement(name = SecuritySchemeName_BearerAuth)}
    )
    @GET
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_VERSION)
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({Config.ROLE_ADMIN})
    @Deamon
    //@CaptureTransaction("admin.version")
    public void version(@Parameter(hidden = true) final SessionContext context) {
        context.response(getVersion()).status(HttpResponseStatus.OK);
    }

    protected String version;

    protected String getVersion() {
        if (version == null) {
            version = BackOffice.agent.getVersion();
        }
        return version;
    }


    @Operation(
            tags = {TAG_APP_ADMIN},
            summary = "Run application self inspection",
            description = "get running application health information",
            responses = {
                    @ApiResponse(responseCode = "200", description = "inspection success with current version"),
                    @ApiResponse(responseCode = "4xx", description = DESC_4xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    ),
                    @ApiResponse(responseCode = "5xx", description = DESC_5xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    )
            },
            security = {
                    @SecurityRequirement(name = SecuritySchemeName_BearerAuth)}
    )
    @GET
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_INSPECTION)
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({Config.ROLE_ADMIN})
    @Deamon
    //@CaptureTransaction("admin.inspect")
    public void inspect(@Parameter(hidden = true) final SessionContext context) {
        HealthMonitor.inspect();
    }

    @Operation(
            tags = {TAG_APP_ADMIN},
            summary = "Graceful shutdown by changing service status",
            description = "pause service if pause param is true, otherwise resume service",
            responses = {
                    @ApiResponse(responseCode = "204", description = "success"),
                    @ApiResponse(responseCode = "4xx", description = DESC_4xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    ),
                    @ApiResponse(responseCode = "5xx", description = DESC_5xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    )
            },
            security = {
                    @SecurityRequirement(name = SecuritySchemeName_BearerAuth)}
    )
    @PUT
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_STATUS)
    @RolesAllowed({Config.ROLE_ADMIN})
    @Deamon
    //@CaptureTransaction("admin.changeStatus")
    public void pause(@QueryParam("pause") boolean pause, @Parameter(hidden = true) final SessionContext context) throws IOException {
        HealthMonitor.pauseService(pause, BootConstant.PAUSE_LOCK_CODE_VIAWEB, "request by " + context.caller());
        context.status(HttpResponseStatus.NO_CONTENT);
    }

    @Operation(
            tags = {TAG_USER_AUTH},
            summary = "User login",
            description = "Accept Form based parameters for login",
            responses = {
                    @ApiResponse(responseCode = "201", description = "success and return JWT token in header " + Config.X_AUTH_TOKEN,
                            headers = {
                                    @Header(name = Config.X_AUTH_TOKEN, schema = @Schema(type = "string"), description = "Generated JWT")
                            },
                            content = @Content(schema = @Schema(implementation = Caller.class))
                    ),
                    @ApiResponse(responseCode = "4xx", description = DESC_4xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    ),
                    @ApiResponse(responseCode = "5xx", description = DESC_5xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    )
            }
    )
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path(Config.CURRENT_VERSION + Config.API_NF_JSECURITYCHECK)
    @Deamon
    //@CaptureTransaction("user.signJWT")
    @Log(requestBody = false, maskDataFields = Config.X_AUTH_TOKEN)
    public Caller longin_jSecurityCheck(@Parameter(required = true) @Nonnull @FormParam("j_username") String userId,
                                        @FormParam("j_password") String password,
                                        @Parameter(hidden = true) final SessionContext context) throws IOException, NamingException {
        return login(auth, userId, password, context);
    }

    @Operation(
            tags = {TAG_USER_AUTH},
            summary = "User login",
            description = "Accept JSON based parameters for login",
            responses = {
                    @ApiResponse(responseCode = "201", description = "success and return JWT token in header " + Config.X_AUTH_TOKEN,
                            headers = {
                                    @Header(name = Config.X_AUTH_TOKEN, schema = @Schema(type = "string"), description = "Generated JWT")
                            },
                            content = @Content(schema = @Schema(implementation = Caller.class))
                    ),
                    @ApiResponse(responseCode = "4xx", description = DESC_4xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    ),
                    @ApiResponse(responseCode = "5xx", description = DESC_5xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    )
            }
    )
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOGIN)
    @Deamon
    //@CaptureTransaction("user.signJWT")
    @Log(requestBody = false, maskDataFields = Config.X_AUTH_TOKEN)
    public Caller longin_JSON(@Valid @Nonnull LoginVo loginVo,
                              @Parameter(hidden = true) final SessionContext context) throws IOException, NamingException {
        return login(auth, loginVo.getUsername(), loginVo.getPassword(), context);
    }

    public Caller login(Authenticator auth, String userId, String password, SessionContext context) throws NamingException {
        if (auth == null) {
            context.error(new Err<>(BootErrorCode.ACCESS_BASE, null, null, null, "Authenticator not provided")).status(HttpResponseStatus.NOT_IMPLEMENTED);
            return null;
        }
        if (!preLogin(userId, password, context)) {
            return null;
        }
        String jwt = auth.signJWT(userId, password, null, AuthConfig.cfg.getJwtTTLMinutes(), context);
        if (jwt == null) {
            context.status(HttpResponseStatus.UNAUTHORIZED);
        } else {
            context.responseHeader(Config.X_AUTH_TOKEN, jwt).status(HttpResponseStatus.CREATED);
        }
        postLogin(context);
        return context.caller();
    }

    protected boolean preLogin(String userId, String password, SessionContext context) {
        return true;
    }

    protected void postLogin(SessionContext context) {
    }

    @Operation(
            tags = {TAG_USER_AUTH},
            summary = "User logout",
            description = "User logout",
            responses = {
                    @ApiResponse(responseCode = "204", description = "success"),
                    @ApiResponse(responseCode = "4xx", description = DESC_4xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    ),
                    @ApiResponse(responseCode = "5xx", description = DESC_5xx,
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    )
            },
            security = {
                    @SecurityRequirement(name = SecuritySchemeName_BearerAuth)}
    )
    @DELETE
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOGIN)
    @Deamon
    //@PermitAll
    //@CaptureTransaction("user.logoutToken")
    public void logout(@Parameter(hidden = true) final ServiceRequest request, @Parameter(hidden = true) final SessionContext context) {
        //Authenticator auth = getAuthenticator();
        if (auth == null) {
            context.error(new Err<>(BootErrorCode.ACCESS_BASE, null, null, null, "Authenticator not provided")).status(HttpResponseStatus.NOT_IMPLEMENTED);
            return;
        }
        //AuthTokenCache authTokenCache = getAuthTokenCache();
        auth.logoutToken(request.getHttpHeaders(), authTokenCache, context);
        context.status(HttpResponseStatus.NO_CONTENT);
    }

    @Operation(hidden = true)
    @POST
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOADTEST)// .../loadtest?delayMilsec=123
    @RolesAllowed({Config.ROLE_ADMIN})
    @Deamon
    public void loadTestBenchmarkPost1(final ServiceRequest request, final SessionContext context, @QueryParam("delayMilsec") long wait) {
        if (wait > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        context.status(HttpResponseStatus.OK).response(request.getHttpRequestPath() + "?delayMilsec=" + wait + request.getHttpPostRequestBody());
    }

    @Operation(hidden = true)
    @POST
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOADTEST + "/{delayMilsec}")
    @Deamon
    public void loadTestBenchmarkPost2(final ServiceRequest request, final SessionContext context, @PathParam("delayMilsec") long wait) {
        if (wait > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        context.status(HttpResponseStatus.OK).response(request.getHttpRequestPath() + request.getHttpPostRequestBody());
    }

    @Operation(hidden = true)
    @GET
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOADTEST)// .../loadtest?delayMilsec=123
    @RolesAllowed({Config.ROLE_ADMIN})
    @Deamon
    public void loadTestBenchmarkGet1(final ServiceRequest request, final SessionContext context, @QueryParam("delayMilsec") long wait) {
        if (wait > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        context.status(HttpResponseStatus.OK).response(request.getHttpRequestPath() + "?delayMilsec=" + wait);
    }

    @Operation(hidden = true)
    @GET
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOADTEST + "/{delayMilsec}")
    @Deamon
    public void loadTestBenchmarkGet2(final ServiceRequest request, final SessionContext context, @PathParam("delayMilsec") long wait) {
        if (wait > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        context.status(HttpResponseStatus.OK).response(request.getHttpRequestPath());
    }

    public interface Config {

        String ROLE_ADMIN = "AppAdmin";

        String CURRENT_VERSION = "";// "/admin";

        //Anonymous Non-Functional API
        String LOAD_BALANCER_HEALTH_CHECK = "/ping";
        String API_NF_LOADTEST = "/loadtest";
        String API_NF_JSECURITYCHECK = "/j_security_check";

        String API_NF_LOGIN = "/login";

        //Role based Non-Functional API
        String API_ADMIN_VERSION = "/version";
        String API_ADMIN_STATUS = "/status";
        String API_ADMIN_INSPECTION = "/inspection";

        String X_AUTH_TOKEN = "X-AuthToken";// Response: JWT from Auth Center
    }
}
