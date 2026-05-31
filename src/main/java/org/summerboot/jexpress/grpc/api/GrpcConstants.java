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

package org.summerboot.jexpress.grpc.api;

import io.grpc.Context;
import io.grpc.Metadata;
import org.summerboot.jexpress.core.session.SessionContext;

public interface GrpcConstants {
    Context.Key<SessionContext> Key_SessionContext = Context.key("SessionContext");

    Metadata.Key<String> Key_Authorization = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    String BEARER_TYPE = "Bearer";

}
