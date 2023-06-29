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

import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.nio.server.multipart.MultipartUtil;
import org.summerboot.jexpress.security.auth.Caller;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.ReferenceCountUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.BootErrorCode;
import io.netty.handler.codec.DecoderException;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
//NOT @ChannelHandler.Sharable due to BootHttpFileUploadHandler is stateful
//NOT @Singleton
public abstract class BootHttpFileUploadHandler extends SimpleChannelInboundHandler<HttpObject> {

    protected Logger log = LogManager.getLogger(this.getClass());

    private static final boolean AUTO_RELEASE = false;
    private static final boolean USER_DISK = true;
    private static final HttpDataFactory HDF = new DefaultHttpDataFactory(USER_DISK);
    //private static final Authenticator auth = new AuthenticatorImple_LDAP();

    protected static final NioConfig uploadCfg = NioConfig.cfg;

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file on exit (in normal exit)
        DiskFileUpload.baseDirectory = uploadCfg.getTempUoloadDir(); // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = true; // should delete file on exit (in normal exit)
        DiskAttribute.baseDirectory = uploadCfg.getTempUoloadDir(); // system temp directory
    }

    public BootHttpFileUploadHandler() {
        super(AUTO_RELEASE);
    }

    private HttpRequest request;
    private boolean isMultipart;
    private HttpPostRequestDecoder httpDecoder;
    private long hitIndex;
    private HttpData partialContent;
    private long fileSizeQuota;
    private Caller caller;
    private Map<String, String> params;

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable ex) {
        if (ex instanceof DecoderException) {
            log.warn(ctx.channel().remoteAddress() + " - caller(" + caller + "): " + ex);
        } else {
            log.warn(ctx.channel().remoteAddress() + " - caller(" + caller + "): " + ex, ex);
        }
        if (ex instanceof OutOfMemoryError) {
            ctx.close();
        }
        //ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (httpDecoder != null) {
            httpDecoder.cleanFiles();
        }
        ctx.fireChannelInactive();
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject httpObject) throws Exception {
        if (httpObject instanceof HttpRequest) {
            request = (HttpRequest) httpObject;
            isMultipart = MultipartUtil.isMultipart(request);
            if (isMultipart) {
                NioCounter.COUNTER_HIT.incrementAndGet();
                hitIndex = NioCounter.COUNTER_BIZ_HIT.incrementAndGet();
                fileSizeQuota = precheck(ctx, request);
                if (fileSizeQuota < 1) {
                    ReferenceCountUtil.release(httpObject);
                    //NioHttpUtil.sendError(ctx, HttpResponseStatus.FORBIDDEN, BootErrorCode.NIO_EXCEED_FILE_SIZE_LIMIT, "file upload not supported", null);
                    TimeUnit.MILLISECONDS.sleep(1000);// give it time to flush the error message to client
                    ctx.channel().close();// the only way to stop uploading is to close socket 
                    return;
                }
                httpDecoder = new HttpPostRequestDecoder(HDF, request);
                httpDecoder.setDiscardThreshold(0);
            }
        }
        if (!isMultipart) {
            //pass to next Handler
            ctx.fireChannelRead(httpObject);
            return;
        }
        if (httpObject instanceof HttpContent) {
            if (httpDecoder != null) {
                try {
                    final HttpContent chunk = (HttpContent) httpObject;
                    httpDecoder.offer(chunk);
                    boolean isOverSized = onPartialChunk(ctx, fileSizeQuota);
                    if (isOverSized) {
                        reset();
                        ServiceError e = new ServiceError(BootErrorCode.NIO_EXCEED_FILE_SIZE_LIMIT, null, String.valueOf(fileSizeQuota), null);
                        NioHttpUtil.sendText(ctx, true, null, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, e.toJson(), null, null, true, null);
                    } else if (chunk instanceof LastHttpContent) {
                        onLastChunk(ctx);
                    }
                } finally {
                    ReferenceCountUtil.release(httpObject);
                }
            } else {
                ctx.fireChannelRead(httpObject);
                //return;
            }
        }
    }

    private void reset() {
        //关闭httpDecoder
        if (httpDecoder != null) {
            httpDecoder.cleanFiles();
            httpDecoder.destroy();
        }
        httpDecoder = null;
        partialContent = null;
    }

    private boolean onPartialChunk(ChannelHandlerContext ctx, long maxAllowedSize) throws IOException {
        long totalReceivedBytes = 0;
        try {
            while (httpDecoder.hasNext()) {
                InterfaceHttpData data = httpDecoder.next();
                if (data != null) {
                    // check if current HttpData is a FileUpload and previously set as partial
                    if (partialContent == data) {
                        log.info(" 100% (FinalSize: " + partialContent.length() + ")");
                        partialContent = null;
                    }
                    // new value
                    //writeHttpData(data);
                    switch (data.getHttpDataType()) {
                        case Attribute:
                            Attribute attribute = (Attribute) data;
                            String value;
                            try {
                                value = attribute.getValue();
                                if (params == null) {
                                    params = new HashMap();
                                }
                                params.put(attribute.getName(), value);
                            } catch (IOException e1) {
                                log.error("read attribute failed", e1);
                            }
                            break;
                        case FileUpload:
                            FileUpload fileUpload = (FileUpload) data;
                            if (fileUpload.isCompleted()) {
                                log.debug("file completed " + fileUpload.length());
                                onFileUploaded(ctx, fileUpload.getFilename(), fileUpload.getFile(), params, caller);
                            }
                            break;
                    }
//                    if (data.getHttpDataType() == HttpDataType.Attribute) {
//                        Attribute attribute = (Attribute) data;
//                        String value;
//                        try {
//                            value = attribute.getValue();
//                        } catch (IOException e1) {
//                            log.error("read attribute failed", e1);
//                        }
//                    } else if (data.getHttpDataType() == HttpDataType.FileUpload) {
//                        FileUpload fileUpload = (FileUpload) data;
//                        if (fileUpload.isCompleted()) {
//                            log.debug("file completed " + fileUpload.length());
//                            onFileUploaded(ctx, fileUpload.getFilename(), fileUpload.getFile(), caller);
//                        }
//                    }
                }
            }

            // Check partial decoding for a FileUpload
            InterfaceHttpData data = httpDecoder.currentPartialHttpData();
            if (data != null && HttpDataType.FileUpload.equals(data.getHttpDataType())) {
                if (partialContent == null) {
                    partialContent = (HttpData) data;
                }
            }
            totalReceivedBytes = partialContent == null
                    ? 0
                    : partialContent.length();

        } catch (EndOfDataDecoderException e1) {
            log.debug("\r\n\r\nEND OF CONTENT CHUNK BY CHUNK\r\n\r\n");
        }
        return totalReceivedBytes > maxAllowedSize;
    }

    private void onLastChunk(ChannelHandlerContext ctx) throws IOException {
//        if (httpDecoder.hasNext()) {
//            partialContent = null;
//            InterfaceHttpData data = httpDecoder.next();
//            if (data != null && HttpDataType.FileUpload.equals(data.getHttpDataType())) {
//                final FileUpload fileUpload = (FileUpload) data;
//                onFileUploaded(ctx, fileUpload.getFilename(), fileUpload.getFile(), params, caller);
//            }
//        }
        reset();
    }

    /**
     *
     * @param ctx
     * @param req
     * @return quota (in bytes) of uploaded file size
     */
    private long precheck(ChannelHandlerContext ctx, HttpRequest req) {
        if (!isValidRequestPath(req.uri()) || !HttpMethod.POST.equals(req.method())) {
            ServiceError e = new ServiceError(BootErrorCode.NIO_HTTP_REQUEST_DECODER_FAILURE, null, "invalid request:" + req.method() + " " + req.uri(), null);
            NioHttpUtil.sendText(ctx, true, null, HttpResponseStatus.BAD_REQUEST, e.toJson(), null, null, true, null);
            return 0;
        }

        final HttpHeaders httpHeaders = req.headers();
//            Set<Cookie> cookies;
//            String value = httpHeaders.get(HttpHeaderNames.COOKIE);
//            if (value == null) {
//                cookies = Collections.emptySet();
//            } else {
//                cookies = ServerCookieDecoder.STRICT.decode(value);
//            }
        ServiceContext context = ServiceContext.build(hitIndex);
        caller = authenticate(httpHeaders, context);
        if (caller == null) {
            ServiceError e = new ServiceError(BootErrorCode.AUTH_INVALID_USER, null, "Unauthorized Caller", null);
            NioHttpUtil.sendText(ctx, true, null, HttpResponseStatus.FORBIDDEN, e.toJson(), null, null, true, null);
            return 0;
        }

        long contentLength;
        String cl = httpHeaders.get(HttpHeaderNames.CONTENT_LENGTH);
        try {
            contentLength = Long.parseLong(cl);
        } catch (RuntimeException ex) {
            ServiceError e = new ServiceError(BootErrorCode.NIO_HTTP_REQUEST_DECODER_FAILURE, null, "Invalid header: " + HttpHeaderNames.CONTENT_LENGTH + "=" + cl, ex);
            NioHttpUtil.sendText(ctx, true, null, HttpResponseStatus.BAD_REQUEST, e.toJson(), null, null, true, null);
            return 0;
        }
        long maxAllowedSize = getCallerFileUploadSizeLimit_Bytes(caller);
        if (contentLength > maxAllowedSize) {
            ServiceError e = new ServiceError(BootErrorCode.NIO_EXCEED_FILE_SIZE_LIMIT, null, String.valueOf(maxAllowedSize), null);
            NioHttpUtil.sendText(ctx, true, null, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, e.toJson(), null, null, true, null);
            return 0;
        }
        return maxAllowedSize;
    }

    protected abstract boolean isValidRequestPath(String httpRequestPath);

    protected abstract Caller authenticate(final HttpHeaders httpHeaders, ServiceContext context);

    protected abstract long getCallerFileUploadSizeLimit_Bytes(Caller caller);

    protected abstract void onFileUploaded(ChannelHandlerContext ctx, String fileName, File file, Map<String, String> params, Caller caller);

}
