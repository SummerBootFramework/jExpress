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
package org.summerboot.jexpress.api.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.summerboot.jexpress.annotation.rest.Daemon;
import org.summerboot.jexpress.annotation.rest.RequiresHealthCheck;
import org.summerboot.jexpress.api.common.ServiceRequest;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.infra.netty.util.NioHttpUtil;

import java.io.File;
import java.io.IOException;

/**
 * 404 error will be responsed as html when extends WebResourceController
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
abstract public class WebResourceController {

    @GET
    @Path("/favicon.ico")
    @Daemon
    @RequiresHealthCheck(BootConstants.HEALTH_CHECKER_NAME_ADMIN)
    public File favicon() {
        return new File(getFaviconPath());
    }

    protected abstract String getFaviconPath();

    /**
     * send web resource images, css, js, etc. to browser
     *
     * @param request
     * @param response
     */
    @GET
    @Path("/{path: .*}")
    @Daemon
    @RequiresHealthCheck(BootConstants.HEALTH_CHECKER_NAME_ADMIN)
    public void requestWebResource(final ServiceRequest request, final SessionContext response) throws IOException {
        NioHttpUtil.sendWebResource(request, response);
    }

    /*@GET
    @Path("/css/{path: .*}")
    public void requestCss(final ServiceRequest request, final SessionContext response) {
        NioHttpUtil.sendWebResource(request, response);
    }

    @GET
    @Path("/js/{path: .*}")
    public void requestJavaScript(final ServiceRequest request, final SessionContext response) {
        NioHttpUtil.sendWebResource(request, response);
    }

    @GET
    @Path("/images/{path: .*}")
    public void requestImage(final ServiceRequest request, final SessionContext response) {
        NioHttpUtil.sendWebResource(request, response);
    }*/
}
