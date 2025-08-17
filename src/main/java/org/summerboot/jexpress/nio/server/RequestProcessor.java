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
package org.summerboot.jexpress.nio.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import org.summerboot.jexpress.nio.server.domain.ProcessorSettings;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public interface RequestProcessor {

    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    ProcessorSettings getProcessorSettings();

    String getDeclaredPath();

    String getProcessedDeclaredPath();

    boolean isRoleBased();

    boolean matches(String httpRequestPath);

    boolean authorizationCheck(final ChannelHandlerContext channelHandlerCtx, final HttpHeaders httpHeaders, final String httpRequestPath, final Map<String, List<String>> queryParams, final String httpPostRequestBody, final SessionContext context, int badRequestErrorCode) throws Throwable;

    Object process(ChannelHandlerContext ctx, HttpHeaders httpRequestHeaders, String httpRequestPath, Map<String, List<String>> queryParams, String httpPostRequestBody, SessionContext context) throws Throwable;

}
