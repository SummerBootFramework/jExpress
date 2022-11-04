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
package org.summerboot.jexpress.boot;

import io.netty.handler.codec.http.HttpResponseStatus;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface BootErrorCode {

    interface CustomHttpStatus {

        HttpResponseStatus UNAVAILABLE_FOR_LEGAL_REASONS = HttpResponseStatus.valueOf(451, "Unavailable For Legal Reasons");
        HttpResponseStatus ABP_POSSIBLE_REJECTION = HttpResponseStatus.valueOf(520, "ABP Possible rejection");
    }

    int OK = 0;

    // NIO
    int NIO_UNEXPECTED_EXECUTOR_FAILURE = 1;
    int NIO_UNEXPECTED_SERVICE_FAILURE = 2;
    int NIO_TOO_MANY_REQUESTS = 3;
    int NIO_BAD_REQUEST = 4;
    int NIO_OUT_OF_MEMORY = 5;
    int NIO_HTTP_REQUEST_DECODER_FAILURE = 6;
    int NIO_EXCEED_FILE_SIZE_LIMIT = 7;
    int NIO_CONTROLLER_UNINITIALIZED = 8;
    int NIO_UNEXPECTED_FAILURE = 9;
    int NIO_WSRS_REQUEST_BAD_DATA = 10;

    //IO
    int IO_ERROR = 20;
    int APP_INTERRUPTED = IO_ERROR + 1;
    int HTTPREQUEST_TIMEOUT = IO_ERROR + 2;
    int HTTPCLIENT_TOO_MANY_CONNECTIONS_REJECT = IO_ERROR + 3;
    int HTTPCLIENT_TIMEOUT = IO_ERROR + 4;
    int HTTPCLIENT_UNEXPECTED_RESPONSE_FORMAT = IO_ERROR + 5;
    int HTTPCLIENT_RPC_FAILED = IO_ERROR + 6;
    int FILE_NOT_ACCESSABLE = IO_ERROR + 11;
    int FILE_NOT_FOUND = IO_ERROR + 12;

    // Auth
    int AUTH_BASE = 40;
    int AUTH_REQUIRE_TOKEN = AUTH_BASE + 1;
    int AUTH_INVALID_TOKEN = AUTH_BASE + 2;
    int AUTH_EXPIRED_TOKEN = AUTH_BASE + 3;
    int AUTH_INVALID_URL = AUTH_BASE + 4;
    int AUTH_LOGIN_FAILED = AUTH_BASE + 5;
    int AUTH_NO_PERMISSION = AUTH_BASE + 6;
    int AUTH_INVALID_USER = AUTH_BASE + 7;

    //Integration
    int ACCESS_ERROR = 50;
    int ACCESS_ERROR_CACHE = ACCESS_ERROR + 1;
    int ACCESS_ERROR_LDAP = ACCESS_ERROR + 2;
    int ACCESS_ERROR_SMTP = ACCESS_ERROR + 3;
    int ACCESS_ERROR_DATABASE = ACCESS_ERROR + 4;
    int ACCESS_ERROR_RPC = ACCESS_ERROR + 5;

}
