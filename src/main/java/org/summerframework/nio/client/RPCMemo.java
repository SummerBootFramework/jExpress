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
package org.summerframework.nio.client;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public interface RPCMemo {

    String MEMO_RPC_UNAUTHORIZED = "RPC_UNAUTHORIZED";

    String MEMO_RPC_REQUEST = "1.RPC_req";
    String MEMO_RPC_REQUEST_DATA = "2.RPC_req.body";
    String MEMO_RPC_RESPONSE = "3.RPC_resp";
    String MEMO_RPC_RESPONSE_DATA = "4.RPC_resp.body";
}
