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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.summerboot.jexpress.boot.annotation.Ping;
import org.summerboot.jexpress.nio.server.domain.ServiceError;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class PingController {

    /**
     * method with @Ping annotation will be handled by BootHttpPingHandler
     */
    @Ping
    @GET
    @Path(BootController.Config.CURRENT_VERSION + BootController.Config.LOAD_BALANCER_HEALTH_CHECK)
    @Operation(
            tags = {"Load Balancing"},
            summary = "ping service status",
            description = "Load Balancer (F5, Nginx, etc) will do the health check via this ping service, if Http Status is not 200(OK), the load Balancer will stop sending new request to this service.<br>"
                    + "Below is an example of F5 config: Basically it's one monitor that does the check to each member in the pool . It will mark each server within the pool member down if it does not receive a 200. <br>"
                    + "<i>GET /myservices/myapp/ping HTTP/1.1\\r\\nConnection: Close\\r\\n\\r\\n</i>",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The service status is healthy"),
                    @ApiResponse(responseCode = "5XX", description = "The service status is unhealthy if response code is not 200",
                            content = @Content(schema = @Schema(implementation = ServiceError.class))
                    )
            }
    )
    public void ping() {
        //method with @Ping annotation will be handled by BootHttpPingHandler
    }
}
