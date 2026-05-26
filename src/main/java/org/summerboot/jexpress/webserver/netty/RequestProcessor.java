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
package org.summerboot.jexpress.webserver.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import org.summerboot.jexpress.controller.SessionContext;
import org.summerboot.jexpress.webserver.domain.ProcessorSettings;

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
