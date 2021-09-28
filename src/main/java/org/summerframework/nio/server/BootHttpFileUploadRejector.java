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

import org.summerframework.nio.server.domain.ServiceResponse;
import org.summerframework.security.auth.Caller;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import java.io.File;
import java.util.Map;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
@ChannelHandler.Sharable
public class BootHttpFileUploadRejector extends BootHttpFileUploadHandler {

    @Override
    protected boolean isValidRequestPath(String httpRequestPath) {
        return false;
    }

    @Override
    protected Caller authenticate(final HttpHeaders httpHeaders, ServiceResponse response) {
        return null;
    }

    @Override
    protected long getCallerFileUploadSizeLimit_Bytes(Caller caller) {
        return 0;
    }

    @Override
    protected void onFileUploaded(ChannelHandlerContext ctx, String fileName, File file, Map<String, String> params, Caller caller) {
    }
}
