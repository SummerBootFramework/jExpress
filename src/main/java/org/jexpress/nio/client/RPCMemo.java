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
package org.jexpress.nio.client;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface RPCMemo {

    String MEMO_RPC_UNAUTHORIZED = "RPC_UNAUTHORIZED";

    String MEMO_RPC_REQUEST = "1.RPC_req";
    String MEMO_RPC_REQUEST_DATA = "2.RPC_req.body";
    String MEMO_RPC_RESPONSE = "3.RPC_resp";
    String MEMO_RPC_RESPONSE_DATA = "4.RPC_resp.body";
}
