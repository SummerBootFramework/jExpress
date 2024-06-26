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
package org.summerboot.jexpress.boot.instrumentation;

import com.google.inject.Singleton;
import jakarta.annotation.Nonnull;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceError;

import java.util.List;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@Singleton
public class BootHealthInspectorImpl implements HealthInspector {

    /**
     * @param args Logger, Boolean
     * @return
     */
    @Override
    public List<Err> ping(Object... args) {
        ServiceError error = new ServiceError(BootConstant.APP_ID + "- ping");
        healthCheck(error, args);
        List<Err> errors = error.getErrors();
        return errors;
    }

    protected void healthCheck(@Nonnull ServiceError error, Object... args) {
    }

}
