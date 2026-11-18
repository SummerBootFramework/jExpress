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
package org.summerboot.jexpress.api.rpc;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface RpcMemo {

    String MEMO_RPC_UNAUTHORIZED = "RPC_UNAUTHORIZED";

    String MEMO_RPC_REQUEST = "rpc.1req";
    String MEMO_RPC_REQUEST_DATA = "rpc.2req.body";
    String MEMO_RPC_RESPONSE_IPS = "rpc.3resp.ips";
    String MEMO_RPC_RESPONSE = "rpc.3resp";
    String MEMO_RPC_RESPONSE_DATA = "rpc.4resp.body";
}
