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
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceRequest;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.security.RolesAllowed;
import javax.naming.NamingException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.integration.cache.AuthTokenCache;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.security.auth.AuthConfig;
import org.summerboot.jexpress.security.auth.Authenticator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;
import org.summerboot.jexpress.security.auth.Caller;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
@Controller
//@Path(CONTEXT_ROOT)
//@Consumes(MediaType.APPLICATION_JSON)
//@Produces(MediaType.APPLICATION_JSON)
@OpenAPIDefinition(//OAS v3
        info = @Info(
                title = "Default Admin API",
                version = SummerApplication.VERSION,
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
@SecurityScheme(name = "BearerAuth", scheme = "bearer", type = SecuritySchemeType.HTTP, bearerFormat = "Authorization: Bearer <token>")
//@SecurityScheme(name = "BasicAuth", scheme = "basic", type = SecuritySchemeType.HTTP)
//@SecurityScheme(name = "ApiKeyAuth", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER)
//@SecurityScheme(name = "OpenID", type = SecuritySchemeType.OPENIDCONNECT, openIdConnectUrl = "https://example.com/.well-known/openid-configuration")
//@SecurityScheme(name = "OAuth2", type = SecuritySchemeType.OAUTH2, flows = @OAuthFlows())
abstract public class BootController extends PingController {

    public static final String TAG_APP_ADMIN = "App Admin";
    public static final String TAG_USER_AUTH = "App User Authentication";

    @Inject
    protected AuthTokenCache authTokenCache;
    //abstract protected AuthTokenCache getAuthTokenCache();

    @Inject
    protected HealthInspector healthInspector;

    @Inject
    protected Authenticator auth;
    //abstract protected Authenticator getAuthenticator();

    @GET
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_VERSION)
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({Config.ROLE_ADMIN})
    //@CaptureTransaction("admin.version")
    @Operation(
            tags = {TAG_APP_ADMIN},
            summary = "Check application version",
            description = "get running application version information",
            //                        parameters = {
            //                            @Parameter(name = "", in = ParameterIn.HEADER, required = true, description = "")},
            responses = {
                @ApiResponse(responseCode = "200", description = "running application version"),
                @ApiResponse(responseCode = "401", description = "caller is not in Admin role",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "4XX", description = "A fault has taken place on client side. Client should not retransmit the same request again, but fix the error first.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "5XX", description = "Something happened on the server side. The client can continue and try again with the request without modification.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                )
            },
            security = {
                @SecurityRequirement(name = "BearerAuth")}
    )
    public void version(@Parameter(hidden = true) final ServiceContext context) {
        context.txt(getVersion()).status(HttpResponseStatus.OK);
    }

    private String version;

    protected String getVersion() {
        if (version == null) {
            version = System.getProperty(SummerApplication.SYS_PROP_APP_VERSION);//Although System.getProperty is a synchronized API, this is  a singleton
        }
        return version;
    }

    @GET
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_INSPECTION)
    @Produces(MediaType.TEXT_HTML)
    @RolesAllowed({Config.ROLE_ADMIN})
    //@CaptureTransaction("admin.inspect")
    @Operation(
            tags = {TAG_APP_ADMIN},
            summary = "Run application self inspection",
            description = "get running application health information",
            responses = {
                @ApiResponse(responseCode = "200", description = "inspection success with current version"),
                @ApiResponse(responseCode = "401", description = "caller is not in Admin role",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "500", description = "inspection error result"),
                @ApiResponse(responseCode = "4XX", description = "A fault has taken place on client side. Client should not retransmit the same request again, but fix the error first.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "5XX", description = "Something happened on the server side. The client can continue and try again with the request without modification.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                )
            },
            security = {
                @SecurityRequirement(name = "BearerAuth")}
    )
    public void inspect(@Parameter(hidden = true) final ServiceContext context) {
        //HealthInspector healthInspector = getHealthInspector();        
        if (healthInspector == null) {
            context.error(new Err(BootErrorCode.ACCESS_ERROR, null, "HealthInspector not provided", null)).status(HttpResponseStatus.NOT_IMPLEMENTED);
            return;
        }
        List<Err> error = healthInspector.ping(true);
        if (error == null || error.isEmpty()) {
            context.txt("inspection passed").errors(null).status(HttpResponseStatus.OK);
        } else {
            context.errors(error).status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_STATUS)
    @RolesAllowed({Config.ROLE_ADMIN})
    //@CaptureTransaction("admin.changeStatus")
    @Operation(
            tags = {TAG_APP_ADMIN},
            summary = "Graceful shutdown by changing service status",
            description = "pause service if pause param is true, otherwise resume service",
            responses = {
                @ApiResponse(responseCode = "204", description = "success"),
                @ApiResponse(responseCode = "401", description = "caller is not in Admin role",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "4XX", description = "A fault has taken place on client side. Client should not retransmit the same request again, but fix the error first.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "5XX", description = "Something happened on the server side. The client can continue and try again with the request without modification.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                )
            },
            security = {
                @SecurityRequirement(name = "BearerAuth")}
    )
    public void changeStatus(@QueryParam("pause") boolean pause, @Parameter(hidden = true) final ServiceContext context) throws IOException {
        HealthMonitor.setPauseStatus(pause, "request by " + context.caller());
        context.status(HttpResponseStatus.NO_CONTENT);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOGIN)
    //@CaptureTransaction("user.login")
    @Operation(
            tags = {TAG_USER_AUTH},
            summary = "User login",
            description = "User login",
            responses = {
                @ApiResponse(responseCode = "201", description = "success and return JWT token in header " + Config.X_AUTH_TOKEN,
                        headers = {
                            @Header(name = Config.X_AUTH_TOKEN, schema = @Schema(type = "string"), description = "Generated JWT")
                        },
                        content = @Content(schema = @Schema(implementation = Caller.class))
                ),
                @ApiResponse(responseCode = "401", description = "Invalid username or password",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "4XX", description = "A fault has taken place on client side. Client should not retransmit the same request again, but fix the error first.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "5XX", description = "Something happened on the server side. The client can continue and try again with the request without modification.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                )
            }
    )
    public Caller login(@Parameter(required = true) @Nonnull @FormParam("j_username") String uid,
            @FormParam("j_password") String pwd,
            @Parameter(hidden = true) final ServiceContext context) throws IOException, NamingException {
        //Authenticator auth = getAuthenticator();
        if (auth == null) {
            context.error(new Err(BootErrorCode.ACCESS_ERROR, null, "Authenticator not provided", null)).status(HttpResponseStatus.NOT_IMPLEMENTED);
            return null;
        }
        String jwt = auth.login(uid, pwd, null, AuthConfig.cfg.getJwtTTLMinutes(), context);
        if (jwt != null) {
            context.responseHeader(Config.X_AUTH_TOKEN, jwt);
        }
        return context.caller();
    }

    @DELETE
    @Path(Config.CURRENT_VERSION + Config.API_USER_LOGOUT)
    //@PermitAll
    //@CaptureTransaction("user.logout")
    @Operation(
            tags = {TAG_USER_AUTH},
            summary = "User logout",
            description = "User logout",
            responses = {
                @ApiResponse(responseCode = "204", description = "success"),
                @ApiResponse(responseCode = "4XX", description = "A fault has taken place on client side. Client should not retransmit the same request again, but fix the error first.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                ),
                @ApiResponse(responseCode = "5XX", description = "Something happened on the server side. The client can continue and try again with the request without modification.",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                )
            },
            security = {
                @SecurityRequirement(name = "BearerAuth")}
    )
    public void logout(@Parameter(hidden = true) final ServiceRequest request, @Parameter(hidden = true) final ServiceContext context) {
        //Authenticator auth = getAuthenticator();
        if (auth == null) {
            context.error(new Err(BootErrorCode.ACCESS_ERROR, null, "Authenticator not provided", null)).status(HttpResponseStatus.NOT_IMPLEMENTED);
            return;
        }
        //AuthTokenCache authTokenCache = getAuthTokenCache();
        auth.logout(request.getHttpHeaders(), authTokenCache, context);
        context.status(HttpResponseStatus.NO_CONTENT);
    }

    @Operation(hidden = true)
    @POST
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOADTEST)// .../loadtest?delayMilsec=123
    @RolesAllowed({Config.ROLE_ADMIN})
    public void loadTestBenchmarkPost1(final ServiceRequest request, final ServiceContext context, @QueryParam("delayMilsec") long wait) {
        if (wait > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        context.status(HttpResponseStatus.OK).txt(request.getHttpRequestPath() + "?delayMilsec=" + wait + request.getHttpPostRequestBody());
    }

    @Operation(hidden = true)
    @POST
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOADTEST + "/{delayMilsec}")
    public void loadTestBenchmarkPost2(final ServiceRequest request, final ServiceContext context, @PathParam("delayMilsec") long wait) {
        if (wait > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        context.status(HttpResponseStatus.OK).txt(request.getHttpRequestPath() + request.getHttpPostRequestBody());
    }

    @Operation(hidden = true)
    @GET
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOADTEST)// .../loadtest?delayMilsec=123
    @RolesAllowed({Config.ROLE_ADMIN})
    public void loadTestBenchmarkGet1(final ServiceRequest request, final ServiceContext context, @QueryParam("delayMilsec") long wait) {
        if (wait > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        context.status(HttpResponseStatus.OK).txt(request.getHttpRequestPath() + "?delayMilsec=" + wait);
    }

    @Operation(hidden = true)
    @GET
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOADTEST + "/{delayMilsec}")
    public void loadTestBenchmarkGet2(final ServiceRequest request, final ServiceContext context, @PathParam("delayMilsec") long wait) {
        if (wait > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(wait);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        context.status(HttpResponseStatus.OK).txt(request.getHttpRequestPath());
    }
}

interface Config {

    String ROLE_ADMIN = "AppAdmin";

    String CURRENT_VERSION = "";// "/admin";

    //Anonymous Non-Functional API
    String LOAD_BALANCER_HEALTH_CHECK = "/ping";
    String API_NF_LOADTEST = "/loadtest";
    String API_NF_LOGIN = "/j_security_check";

    //Role based Non-Functional API
    String API_USER_LOGOUT = "/logout";
    String API_ADMIN_VERSION = "/version";
    String API_ADMIN_STATUS = "/status";
    String API_ADMIN_INSPECTION = "/inspection";

    String X_AUTH_TOKEN = "X-AuthToken";// Response: JWT from Auth Center
}
