/*
 * Copyright 2005 The Summer Boot Framework Project
 *
 * The Summer Boot Framework Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.summerframework.boot.instrumentation;

import org.summerframework.nio.server.NioServer;
import org.summerframework.nio.server.domain.Error;
import org.summerframework.nio.server.domain.ServiceError;
import org.summerframework.util.JsonUtil;
import com.google.inject.Singleton;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
@Singleton
public class BootHealthInspectorImpl implements HealthInspector {

    /**
     *
     * @param args Logger, Boolean
     * @return
     */
    @Override
    public List<Error> ping(Object... args) {
        boolean changeNIOerviceStatus = false;
        Logger callerLog = null;
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof Boolean) {
                    changeNIOerviceStatus = (Boolean) arg;
                } else if (arg instanceof Logger) {
                    callerLog = (Logger) arg;
                }
            }
        }
        ServiceError error = new ServiceError();
        healthCheck(error, callerLog);
        List<Error> errors = error.getErrors();
        //check result
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator()).append("Self Inspection ");
        if (errors == null || errors.isEmpty()) {
            NioServer.setServiceHealthOk(true, null);
            sb.append("passed");
            if (callerLog != null) {
                callerLog.info(sb);
            }
        } else {
            String inspectionReport;
            try {
                inspectionReport = JsonUtil.toJson(errors, true, true);
            } catch (Throwable ex) {
                inspectionReport = "total " + errors.size();
            }
            if (changeNIOerviceStatus) {
                NioServer.setServiceHealthOk(false, inspectionReport);
            }
            sb.append("failed: ").append(inspectionReport);
            if (callerLog != null) {
                callerLog.error(sb);
            }
        }
        return errors;
    }

    protected void healthCheck(@Nonnull ServiceError error, @Nullable Logger callerLog) {
    }

}
