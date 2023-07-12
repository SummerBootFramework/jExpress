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
import java.util.Map;
import org.summerboot.jexpress.boot.annotation.Unique;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Unique(name = "SystemErrorCode", type = int.class)
public interface BootErrorCode {

    Map<Integer, Integer> Mapping = BackOffice1.agent.getBootErrorCodeMapping();

    interface CustomHttpStatus {

        HttpResponseStatus UNAVAILABLE_FOR_LEGAL_REASONS = HttpResponseStatus.valueOf(451, "Unavailable For Legal Reasons");
        HttpResponseStatus ABP_POSSIBLE_REJECTION = HttpResponseStatus.valueOf(520, "ABP Possible rejection");
    }

    private static int getErrorCode(int code) {
        if (Mapping == null) {
            return code;
        }
        Integer ret = Mapping.get(code);
        return ret == null ? code : ret;
    }

    int OK = getErrorCode(0);

    // NIO
    int NIO_BASE = getErrorCode(1);
    int NIO_TOO_MANY_REQUESTS = getErrorCode(NIO_BASE + 1);
    int NIO_UNEXPECTED_EXECUTOR_FAILURE = getErrorCode(NIO_BASE + 2);
    int NIO_UNEXPECTED_SERVICE_FAILURE = getErrorCode(NIO_BASE + 3);
    int NIO_UNEXPECTED_PROCESSOR_FAILURE = getErrorCode(NIO_BASE + 4);
    int NIO_FILE_UPLOAD_BAD_REQUEST = getErrorCode(NIO_BASE + 5);
    int NIO_FILE_UPLOAD_BAD_LENGTH = getErrorCode(NIO_BASE + 6);
    int NIO_FILE_UPLOAD_EXCEED_SIZE_LIMIT = getErrorCode(NIO_BASE + 7);
    int NIO_REQUEST_BAD_HEADER = getErrorCode(NIO_BASE + 8);
    int NIO_REQUEST_BAD_DATA = getErrorCode(NIO_BASE + 9);
    int NIO_REQUEST_BAD_ENCODING = getErrorCode(NIO_BASE + 10);
    int NIO_REQUEST_BAD_DOWNLOAD = getErrorCode(NIO_BASE + 11);

    //IO
    int IO_BASE = getErrorCode(20);
    int APP_INTERRUPTED = getErrorCode(IO_BASE + 1);
    int HTTP_REQUEST_TIMEOUT = getErrorCode(IO_BASE + 2);// a context is not received within a specified time period.
    int HTTPCLIENT_TOO_MANY_CONNECTIONS_REJECT = getErrorCode(IO_BASE + 3);
    int HTTP_CONNECTION_TIMEOUT = getErrorCode(IO_BASE + 4);// a connection, over which an HttpRequest is intended to be sent, is not successfully established within a specified time period.
    int HTTPCLIENT_UNEXPECTED_RESPONSE_FORMAT = getErrorCode(IO_BASE + 5);
    int HTTPCLIENT_RPC_FAILED = getErrorCode(IO_BASE + 6);
    int NETWORK_ERROR = getErrorCode(IO_BASE + 7);
    int FILE_NOT_ACCESSABLE = getErrorCode(IO_BASE + 8);
    int FILE_NOT_FOUND = getErrorCode(IO_BASE + 9);

    // Auth
    int AUTH_BASE = getErrorCode(40);
    int AUTH_REQUIRE_TOKEN = getErrorCode(AUTH_BASE + 1);
    int AUTH_INVALID_TOKEN = getErrorCode(AUTH_BASE + 2);
    int AUTH_EXPIRED_TOKEN = getErrorCode(AUTH_BASE + 3);
    int AUTH_INVALID_URL = getErrorCode(AUTH_BASE + 4);
    int AUTH_LOGIN_FAILED = getErrorCode(AUTH_BASE + 5);
    int AUTH_NO_PERMISSION = getErrorCode(AUTH_BASE + 6);
    int AUTH_INVALID_USER = getErrorCode(AUTH_BASE + 7);

    //Integration
    int ACCESS_BASE = getErrorCode(50);
    int ACCESS_ERROR_CACHE = getErrorCode(ACCESS_BASE + 1);
    int ACCESS_ERROR_LDAP = getErrorCode(ACCESS_BASE + 2);
    int ACCESS_ERROR_SMTP = getErrorCode(ACCESS_BASE + 3);
    int ACCESS_ERROR_DATABASE = getErrorCode(ACCESS_BASE + 4);
    int ACCESS_ERROR_RPC = getErrorCode(ACCESS_BASE + 5);

}
