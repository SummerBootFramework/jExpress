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

package org.summerboot.jexpress.infra.grpc.client;


import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.summerboot.jexpress.boot.lifecycle.app.IdleEventMonitor;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class GrpcClientInterceptor implements ClientInterceptor {
    private IdleEventMonitor idleEventMonitor;

    public GrpcClientInterceptor(IdleEventMonitor idleEventMonitor) {
        this.idleEventMonitor = idleEventMonitor;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        final String methodSignature = method.getFullMethodName();

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Listener<RespT> wrappingListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        try {
                            // Regardless of whether the call succeeds, times out, or results in an error, the idle timer is reset upon completion.
                            idleEventMonitor.resetIdleTimer(methodSignature);
                        } finally {
                            super.onClose(status, trailers);
                        }
                    }
                };
                super.start(wrappingListener, headers);
            }
        };
    }
}
