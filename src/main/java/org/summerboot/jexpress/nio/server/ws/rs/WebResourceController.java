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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.summerboot.jexpress.nio.server.NioHttpUtil;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.nio.server.domain.ServiceRequest;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class WebResourceController {

    /**
     * send web resource images, css, js, etc. to browser
     *
     * @param request
     * @param response
     */
    @GET
    @Path("/{path: .*}")
    public void requestWebResource(final ServiceRequest request, final ServiceContext response) {
        NioHttpUtil.sendWebResource(request, response);
    }

    /*@GET
    @Path("/css/{path: .*}")
    public void requestCss(final ServiceRequest request, final ServiceContext response) {
        NioHttpUtil.sendWebResource(request, response);
    }

    @GET
    @Path("/js/{path: .*}")
    public void requestJavaScript(final ServiceRequest request, final ServiceContext response) {
        NioHttpUtil.sendWebResource(request, response);
    }

    @GET
    @Path("/image/{path: .*}")
    public void requestImage(final ServiceRequest request, final ServiceContext response) {
        NioHttpUtil.sendWebResource(request, response);
    }*/
}
