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
package org.summerboot.jexpress.integration.http.rpc;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.summerboot.jexpress.core.error.BootErrorCode;
import org.summerboot.jexpress.core.error.Err;
import org.summerboot.jexpress.web.jackson.ServiceErrorConvertible;

import java.util.List;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class RpcError implements ServiceErrorConvertible {

    @Override
    public boolean isSingleError() {
        return true;
    }

    @Override
    public Err toServiceError(HttpResponseStatus status) {
        return new Err(BootErrorCode.ACCESS_ERROR_RPC, null, null, null, "Default RPC error");
    }

    @Override
    public List<Err> toServiceErrors(HttpResponseStatus status) {
        return null;
    }

}
