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
package org.summerboot.jexpress.nio.server.domain;

import org.apache.commons.lang3.StringUtils;
import org.summerboot.jexpress.boot.BootConstant;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class ProcessorSettings {

    protected String httpServiceResponseHeaderName_ServerTimestamp = BootConstant.RESPONSE_HEADER_KEY_TS;

    protected String httpServiceResponseHeaderName_Reference = BootConstant.RESPONSE_HEADER_KEY_REF;

    public String getHttpServiceResponseHeaderName_ServerTimestamp() {
        return httpServiceResponseHeaderName_ServerTimestamp;
    }

    public void setHttpServiceResponseHeaderName_ServerTimestamp(String httpServiceResponseHeaderName_ServerTimestamp) {
        this.httpServiceResponseHeaderName_ServerTimestamp = StringUtils.isBlank(httpServiceResponseHeaderName_ServerTimestamp) ? null : httpServiceResponseHeaderName_ServerTimestamp;
    }

    public String getHttpServiceResponseHeaderName_Reference() {
        return httpServiceResponseHeaderName_Reference;
    }

    public void setHttpServiceResponseHeaderName_Reference(String httpServiceResponseHeaderName_Reference) {
        this.httpServiceResponseHeaderName_Reference = StringUtils.isBlank(httpServiceResponseHeaderName_Reference) ? null : httpServiceResponseHeaderName_Reference;
    }

    protected LogSettings logSettings;

    public LogSettings getLogSettings() {
        return getLogSettings(false);
    }

    public LogSettings getLogSettings(boolean createIfNull) {
        if (logSettings == null && createIfNull) {
            logSettings = new LogSettings();
        }
        return logSettings;
    }

    public void setLogSettings(LogSettings logSettings) {
        this.logSettings = logSettings;
    }

    public class LogSettings {

        protected boolean logRequestHeader;

        protected boolean logRequestBody;

        protected boolean logResponseHeader;

        protected boolean logResponseBody;

        protected List<String> protectDataFieldsFromLogging;


        public void removeDuplicates() {
            if (protectDataFieldsFromLogging != null) {
                protectDataFieldsFromLogging = protectDataFieldsFromLogging.stream().distinct().collect(Collectors.toList());
            }
        }

        public boolean isLogRequestHeader() {
            return logRequestHeader;
        }

        public void setLogRequestHeader(boolean logRequestHeader) {
            this.logRequestHeader = logRequestHeader;
        }

        public boolean isLogRequestBody() {
            return logRequestBody;
        }

        public void setLogRequestBody(boolean logRequestBody) {
            this.logRequestBody = logRequestBody;
        }

        public boolean isLogResponseHeader() {
            return logResponseHeader;
        }

        public void setLogResponseHeader(boolean logResponseHeader) {
            this.logResponseHeader = logResponseHeader;
        }

        public boolean isLogResponseBody() {
            return logResponseBody;
        }

        public void setLogResponseBody(boolean logResponseBody) {
            this.logResponseBody = logResponseBody;
        }

        public List<String> getProtectDataFieldsFromLogging() {
            return protectDataFieldsFromLogging;
        }

        public void setProtectDataFieldsFromLogging(List<String> protectDataFieldsFromLogging) {
            this.protectDataFieldsFromLogging = protectDataFieldsFromLogging;
        }
    }

}
