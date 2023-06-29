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
package org.summerboot.jexpress.nio.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.summerboot.jexpress.security.auth.Caller;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public abstract class BootServerInterceptor implements ServerInterceptor {

    public static final Context.Key<String> CONTEXT_KEY_USER_ID = Context.key("uid");

    public static final Context.Key<Caller> CONTEXT_KEY_CALLER = Context.key("caller");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String headerValueAuthorization = metadata.get(BearerAuthCredential.AUTHORIZATION_METADATA_KEY);

        Status status;
        if (headerValueAuthorization == null) {
            status = Status.UNAUTHENTICATED.withDescription("Authorization header is missing");
        } else if (!headerValueAuthorization.startsWith(BearerAuthCredential.BEARER_TYPE)) {
            status = Status.UNAUTHENTICATED.withDescription("Unknown authorization type, non bearer token provided");
        } else {
            try {
                String jwt = headerValueAuthorization.substring(BearerAuthCredential.BEARER_TYPE.length()).trim();
                Caller caller = buildCaller(jwt);
                Context ctx = Context.current().withValue(CONTEXT_KEY_CALLER, caller);
                return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
            } catch (Throwable ex) {
                status = Status.UNAUTHENTICATED.withDescription(ex.getMessage()).withCause(ex);
            }
        }

        serverCall.close(status, metadata);
        return new ServerCall.Listener<ReqT>() {
        };
    }

    abstract protected Caller buildCaller(String jwt);

    /*
    @Override
    protected Caller buildCaller(String jwt) {
        User user = new User(888L, "mockuser");
        return user;
    }
     */
}
