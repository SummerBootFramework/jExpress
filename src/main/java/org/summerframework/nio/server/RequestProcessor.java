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
package org.summerframework.nio.server;

import org.summerframework.nio.server.domain.ServiceContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
public interface RequestProcessor {

    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    String getDeclaredPath();

    boolean isRoleBased();

    boolean matches(String httpRequestPath);

    void process(ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, ServiceContext context, int BAD_REQUEST) throws Throwable;

}
