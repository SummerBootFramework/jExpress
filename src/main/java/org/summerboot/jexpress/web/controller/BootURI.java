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
package org.summerboot.jexpress.web.controller;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface BootURI {

    String CURRENT_VERSION = "";// "/admin";

    // Role
    String ROLE_ADMIN = "AppAdmin";

    // Header
    String X_AUTH_TOKEN = "X-AuthToken";

    // Anonymous Non-Functional API
    String LOAD_BALANCER_PING = "/ping";
    String API_NF_JSECURITYCHECK = "/j_security_check";
    String API_NF_LOGIN = "/login";
    String API_NF_OTT = "/ott";
    String API_NF_LOADTEST = "/loadtest";

    // Admin role based Non-Functional API
    String API_ADMIN_VERSION = "/version";
    String API_ADMIN_CheckHealth = "/checkhealth";
    String API_ADMIN_GracefulShutdown = "/gracefulshutdown";
}
