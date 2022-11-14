package org.summerboot.jexpress.nio.server;

import co.elastic.apm.api.CaptureTransaction;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceRequest;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import com.google.inject.Inject;
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
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.summerboot.jexpress.boot.SummerApplication;
import org.summerboot.jexpress.boot.annotation.Ping;
import org.summerboot.jexpress.boot.instrumentation.HealthMonitor;

/**
 *
 * @author Changski Tie Zheng Zhang, Annie Liu
 */
@Singleton
@Controller
//@Path(CONTEXT_ROOT)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
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
abstract public class BootController {

//    @Inject
    protected AuthTokenCache cache = null;
    @Inject
    protected HealthInspector healthInspector;

    //@Inject
    protected Authenticator auth;

    @Operation(
            tags = {"Load Balancing"},
            summary = "ping service status",
            description = "Load Balancer（F5, Nginx, etc） will do the health check via this ping service, if Http Status is not 200(OK), the load Balancer will stop sending new request to this service.<br>"
            + "Below is an example of F5 config: Basically it’s one monitor that does the check to each member in the pool . It will mark each server within the pool member down if it does not receive a 200. <br>"
            + "<i>GET /myservices/myapp/ping HTTP/1.1\\r\\nConnection: Close\\r\\n\\r\\n</i>",
            responses = {
                @ApiResponse(responseCode = "200", description = "The service status is healthy"),
                @ApiResponse(responseCode = "5XX", description = "The service status is unhealthy if response code is not 200",
                        content = @Content(schema = @Schema(implementation = ServiceError.class))
                )
            }
    )
    @GET
    @Path(Config.LOAD_BALANCER_HEALTH_CHECK)
    @Ping
    public void ping() {
        //to generate API only, the ping service is handled by framework
    }

    @Operation(
            tags = {"Admin"},
            summary = "get version",
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
    @GET
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_VERSION)
    @RolesAllowed({Config.ROLE_ADMIN})
    @CaptureTransaction("admin.version")
    public void version(@Parameter(hidden = true) final ServiceContext context) {
        context.txt(getVersion()).status(HttpResponseStatus.OK);
    }

    protected String getVersion() {
        return SummerApplication.version();
    }

    @Operation(
            tags = {"Admin"},
            summary = "do inspection",
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
    @GET
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_INSPECTION)
    @RolesAllowed({Config.ROLE_ADMIN})
    @CaptureTransaction("admin.inspect")
    public void inspect(@Parameter(hidden = true) final ServiceContext context) {
        List<Err> error = healthInspector.ping(true);
        if (error == null || error.isEmpty()) {
            context.txt("inspection passed").errors(null).status(HttpResponseStatus.OK);
        } else {
            context.errors(error).status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(
            tags = {"Admin"},
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
    @PUT
    @Path(Config.CURRENT_VERSION + Config.API_ADMIN_STATUS)
    @RolesAllowed({Config.ROLE_ADMIN})
    @CaptureTransaction("admin.changeStatus")
    public void changeStatus(@QueryParam("pause") boolean pause, @Parameter(hidden = true) final ServiceContext context) throws IOException {
        HealthMonitor.setPauseStatus(pause, "request by " + context.caller());
        context.status(HttpResponseStatus.NO_CONTENT);
    }

    @Operation(
            tags = {"User"},
            summary = "login",
            description = "User login",
            responses = {
                @ApiResponse(responseCode = "201", description = "success and return JWT token in header " + Config.X_AUTH_TOKEN,
                        headers = {
                            @Header(name = Config.X_AUTH_TOKEN, schema = @Schema(type = "string"), description = "Generated JWT")
                        }
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
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path(Config.CURRENT_VERSION + Config.API_NF_LOGIN)
    @CaptureTransaction("user.login")
    public void login(@Parameter(required = true) @Nonnull @FormParam("j_username") String uid,
            @FormParam("j_password") String pwd,
            @Parameter(hidden = true) final ServiceContext context) throws IOException, NamingException {
        String jwt = auth.authenticate(uid, pwd, AuthConfig.instance(AuthConfig.class).getJwtTTLMinutes(), context);
        if (jwt != null) {
            context.responseHeader(Config.X_AUTH_TOKEN, jwt);
        }
    }

    @Operation(
            tags = {"User"},
            summary = "logout",
            description = "User out",
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
    @DELETE
    @Path(Config.CURRENT_VERSION + Config.API_USER_LOGOUT)
    @PermitAll
    @CaptureTransaction("user.logout")
    public void logout(@Parameter(hidden = true) final ServiceRequest request, @Parameter(hidden = true) final ServiceContext context) {
        auth.logout(request.getHttpHeaders(), cache, context);
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

    String CURRENT_VERSION ="";// "/admin";

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
