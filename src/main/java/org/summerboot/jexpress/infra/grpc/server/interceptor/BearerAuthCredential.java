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
package org.summerboot.jexpress.infra.grpc.server.interceptor;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import org.summerboot.jexpress.infra.grpc.server.GrpcConstants;

import java.util.concurrent.Executor;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class BearerAuthCredential extends CallCredentials {

    protected final String jwt;

    public BearerAuthCredential(String jwt) {
        this.jwt = jwt;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier metadataApplier) {
        executor.execute(() -> {
            try {
                Metadata headers = new Metadata();
                headers.put(GrpcConstants.Key_Authorization, GrpcConstants.BEARER_TYPE + " " + jwt);
                metadataApplier.apply(headers);
            } catch (Throwable ex) {
                metadataApplier.fail(Status.UNAUTHENTICATED.withCause(ex));
            }
        });
    }
}
