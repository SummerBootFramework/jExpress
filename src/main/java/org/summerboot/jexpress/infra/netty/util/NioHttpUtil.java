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
package org.summerboot.jexpress.infra.netty.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedStream;
import io.netty.util.AsciiString;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.Tika;
import org.summerboot.jexpress.api.cache.SimpleLocalCache;
import org.summerboot.jexpress.api.common.BootErrorCode;
import org.summerboot.jexpress.api.common.Err;
import org.summerboot.jexpress.api.common.ResponseEncoder;
import org.summerboot.jexpress.api.common.ServiceRequest;
import org.summerboot.jexpress.api.common.SessionContext;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstants;
import org.summerboot.jexpress.infra.netty.ErrorAuditor;
import org.summerboot.jexpress.infra.netty.config.NioConfig;
import org.summerboot.jexpress.integration.cache.local.SimpleLocalCacheImpl;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.util.net.GeoIpUtil;
import org.summerboot.jexpress.util.runtime.ApplicationUtil;
import org.summerboot.jexpress.util.time.TimeUtil;
import org.summerboot.jexpress.webserver.domain.ProcessorSettings;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
public class NioHttpUtil {

    protected static final Logger log = LogManager.getLogger(NioHttpUtil.class.getName());

    //security
    public static final String HTTP_HEADER_AUTH_TOKEN = "Authorization";// "X-AuthToken";// "X_Authorization"; //RFC 7235, sec. 4.2
    public static final String HTTP_HEADER_AUTH_TYPE = "Bearer";// RFC6750, https://tools.ietf.org/html/rfc6750
//    protected static String HeaderName_ServerTimestamp = NioConfig.cfg.getHttpServiceResponseHeaderName_ServerTimestamp();
//    protected static String HeaderName_Reference = NioConfig.cfg.getHttpServiceResponseHeaderName_Reference();

    // <img src="data:image/png;base64,<base64 str here>" alt="Red dot" />
    // <object type="application/pdf" data="data:application/pdf;base64,<base64 str here>"/>
    public static String encodeMimeBase64(File file) throws IOException {
        byte[] contentBytes = Files.readAllBytes(file.toPath());
        return Base64.getMimeEncoder().encodeToString(contentBytes);
    }

    public static String encodeMimeBase64(byte[] contentBytes) {
        return Base64.getMimeEncoder().encodeToString(contentBytes);
    }

    public static byte[] decodeMimeBase64(String contentBase64) {
        return Base64.getMimeDecoder().decode(contentBase64);
    }

    public static void decodeMimeBase64(String contentBase64, File dest) throws IOException {
        byte[] contentBytes = Base64.getMimeDecoder().decode(contentBase64);
        FileUtils.writeByteArrayToFile(dest, contentBytes);
    }

    public static final AsciiString KEEP_ALIVE = new AsciiString("keep-alive");
    public static final AsciiString CONNECTION = new AsciiString("Connection");

    private static void sendRedirect(ChannelHandlerContext ctx, String newUri, HttpResponseStatus status, HttpHeaders responseHeaders) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);//HttpResponseStatus.FOUND, HttpResponseStatus.PERMANENT_REDIRECT : HttpResponseStatus.TEMPORARY_REDIRECT
        if (responseHeaders != null) {
            resp.headers().set(responseHeaders);
        }
        resp.headers().set(HttpHeaderNames.LOCATION, newUri);
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }


    public static long sendResponse(ChannelHandlerContext ctx, boolean isKeepAlive, final SessionContext sessionContext, final ErrorAuditor errorAuditor, final ProcessorSettings processorSettings) {
        String headerKey_reference;
        String headerKey_serverTimestamp;
        if (processorSettings == null) {
            headerKey_reference = BootConstants.RESPONSE_HEADER_KEY_REF;
            headerKey_serverTimestamp = BootConstants.RESPONSE_HEADER_KEY_TS;
        } else {
            headerKey_reference = processorSettings.getHttpServiceResponseHeaderName_Reference();
            headerKey_serverTimestamp = processorSettings.getHttpServiceResponseHeaderName_ServerTimestamp();
        }
        sessionContext.responseHeader(headerKey_reference, sessionContext.txId());
        sessionContext.responseHeader(headerKey_serverTimestamp, OffsetDateTime.now().format(TimeUtil.ISO_ZONED_DATE_TIME3));
        final HttpResponseStatus status = sessionContext.status();

        if (sessionContext.file() != null) {
            return sendFile(ctx, isKeepAlive, sessionContext, errorAuditor, processorSettings, sessionContext.responseHeaders());
        }
        if (sessionContext.data() != null) {
            return sendData(ctx, isKeepAlive, sessionContext, errorAuditor, processorSettings, sessionContext.responseHeaders());
        }
        if (sessionContext.redirect() != null) {
            sendRedirect(ctx, sessionContext.redirect(), status, sessionContext.responseHeaders());
            return 0;
        }

        boolean hasErrorContent = StringUtils.isEmpty(sessionContext.txt()) && status.code() >= 400;
        if (hasErrorContent) {
            if (sessionContext.error() == null) {
                sessionContext.error(null);
            }
            String clientAcceptContentType = sessionContext.clientAcceptContentType();
            String errorResponse;
            if (clientAcceptContentType != null && clientAcceptContentType.contains("xml")) {
                errorResponse = sessionContext.error().toXML();
                sessionContext.contentType(MediaType.APPLICATION_XML);
            } else {
                errorResponse = sessionContext.error().toJson();
                sessionContext.contentType(MediaType.APPLICATION_JSON);
            }
            if (errorAuditor != null) {
                errorResponse = errorAuditor.beforeSendingError(errorResponse);
            }
            sessionContext.response(errorResponse);
        }

        if (HttpResponseStatus.OK.equals(status) && sessionContext.autoConvertBlank200To204() && StringUtils.isEmpty(sessionContext.txt())) {
            sessionContext.status(HttpResponseStatus.NO_CONTENT);
        }
        return sendText(ctx, isKeepAlive, sessionContext.responseHeaders(), sessionContext.status(), sessionContext.txt(), sessionContext.contentType(), sessionContext.charsetName(), true, sessionContext.responseEncoder());
    }

    //protected static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static long sendText(ChannelHandlerContext ctx, boolean isKeepAlive, HttpHeaders serviceHeaders, HttpResponseStatus status, String content, String contentType, String charsetName, boolean flush, ResponseEncoder responseEncoder) {
        if (content == null) {
            content = "";
        }
        if (responseEncoder != null) {
            content = responseEncoder.encode(content);
        }
        //FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(content.getBytes(CharsetUtil.UTF_8)));
        byte[] contentBytes;
        if (charsetName == null) {
            Charset DEFAULT_CHARSET = NioConfig.cfg.getDefaultResponseCharset();
            contentBytes = content.getBytes(DEFAULT_CHARSET);
            charsetName = DEFAULT_CHARSET.name();
        } else {
            try {
                contentBytes = content.getBytes(charsetName);
            } catch (UnsupportedEncodingException ex) {
                if (log.isWarnEnabled()) {
                    String error = SecurityUtil.sanitizeCRLF("Unsupported Header (Accept-Charset: " + charsetName + "): " + ex.getMessage());
                    log.warn(error);
                }
                Charset DEFAULT_CHARSET = NioConfig.cfg.getDefaultResponseCharset();
                contentBytes = content.getBytes(DEFAULT_CHARSET);
                charsetName = DEFAULT_CHARSET.name();
            }
        }
//        int a = 252;//"ü"
//        byte[] b = {(byte) a};
//        contentBytes = b;
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(contentBytes));
        HttpHeaders h = resp.headers();
        if (serviceHeaders != null) {
            //headers.forEach((k, v) -> h.set(k, v));
            h.set(serviceHeaders);
        }
        if (contentType != null) {
            h.set(HttpHeaderNames.CONTENT_TYPE, contentType + ";charset=" + charsetName);
        }
        int responseDataBytes = resp.content().readableBytes();
        h.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(responseDataBytes));

        // send
        if (isKeepAlive) {//HttpUtil.isKeepAlive(req);
            // Add keep alive responseHeader as per:
            // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            h.set(HttpHeaderNames.CONNECTION, KEEP_ALIVE);
            if (flush) {
                ctx.writeAndFlush(resp);
            } else {
                ctx.write(resp);
            }
        } else {
            // If keep-alive is off, close the connection once the content is fully written.
            if (flush) {
                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
            } else {
                ctx.write(resp).addListener(ChannelFutureListener.CLOSE);
            }
        }
        if (serviceHeaders != null) {
            serviceHeaders.set(h);
        }
        return responseDataBytes;
    }

    private static long sendData(ChannelHandlerContext ctx, boolean isKeepAlive, final SessionContext context, final ErrorAuditor errorAuditor, final ProcessorSettings processorSettings, HttpHeaders responseHeaders) {
        byte[] data = context.data();
        long dataSize = data.length;
        // 1. 创建 HTTP 响应对象
        /*FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(data) // 包装 byte[] 为 ByteBuf
        );
        long dataSize = response.content().readableBytes();*/
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        // 2. 设置 HTTP 头信息（让浏览器识别为文件下载）
        HttpHeaders headers = response.headers();
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM); // 二进制流
        headers.set(HttpHeaderNames.CONTENT_LENGTH, dataSize);
        // 设置附件下载的文件名
        String cd = context.contentDescription(); //String.format("attachment; filename=\"%s\"", fileName);
        if (StringUtils.isBlank(cd)) {
            context.downloadFleName(context.txId());
            cd = context.contentDescription();
        }
        final String contentDisposition = cd;
        headers.set(HttpHeaderNames.CONTENT_DISPOSITION, contentDisposition);

        // 3.1. 只 write，不 flush
        ctx.write(response); // 只 write，不 flush
        // 3.2. 将 byte[] 包装成输入流，并转为 Netty 的 ChunkedStream
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        // 这里的 8192 是每块的大小（8KB），可根据需要调整
        ChunkedStream chunkedStream = new ChunkedStream(inputStream, 8192);
        // 3.3. 写入分块数据，并获取 ChannelFuture
        // ChunkedWriteHandler 会捕获这个对象并分批 flush
        ChannelFuture downloadFuture = ctx.writeAndFlush(chunkedStream, ctx.newProgressivePromise());
        // 3.4. 绑定监听器记录详细过程
        downloadFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                // total 会自动对应 fileBytes.length
                double percent = ((double) progress / total) * 100;
                log.debug(() -> contentDisposition + " -> Transfer progress: " + percent + "% " + progress + " / " + total);
            }

            @Override
            public void operationComplete(ChannelProgressiveFuture future) {
                if (future.isSuccess()) {
                    log.debug(() -> contentDisposition + " -> Transfer complete: " + dataSize);
                    ctx.close(); // 如果不是 keep-alive 可以选择关闭连接
                } else {
                    log.error(() -> contentDisposition + " -> Transfer failed:" + future.cause().getMessage());
                }
            }
        });
        return dataSize;
    }

    private static long sendFile(ChannelHandlerContext ctx, boolean isKeepAlive, final SessionContext context, final ErrorAuditor errorAuditor, final ProcessorSettings processorSettings, HttpHeaders responseHeaders) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, context.status());
        HttpHeaders h = response.headers();
        if (responseHeaders != null) {
            h.set(responseHeaders);
        }
        h.set(context.responseHeaders());
        long fileLength = -1;
        RandomAccessFile randomAccessFile = null;
        File file = context.file();
        long dataSize = file.length();
        String filePathRequested = file.getAbsolutePath();
        context.memo("sendFile.requested", filePathRequested);
        String filePathChecked = SecurityUtil.escape4Filename(filePathRequested);
        context.memo("sendFile.checked", filePathRequested);
        file = new File(filePathChecked);
        if (!SecurityUtil.precheckFile(file, context)) {
            file = buildErrorFile(context);
            context.response(file, false);
            return sendResponse(ctx, isKeepAlive, context, errorAuditor, processorSettings);
        }

        String filePath = file.getName();
        try {
            randomAccessFile = new RandomAccessFile(file, "r");// CWE-404 False Positive: try with resource will close the IO while the async thread is still using it.
            RandomAccessFile raf = randomAccessFile;
            fileLength = randomAccessFile.length();

            if (isKeepAlive) {
                // Add keep alive responseHeader as per:
                // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
                h.set(HttpHeaderNames.CONNECTION, KEEP_ALIVE);
            }
            ctx.write(response);
            // the sending progress
            ChannelFuture downloadFuture = ctx.write(new ChunkedFile(randomAccessFile, 0, fileLength, 8192), ctx.newProgressivePromise());
            downloadFuture.addListener(new ChannelProgressiveFutureListener() { // CWE-404 False Positive prove
                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                    // total 会自动对应 fileBytes.length
                    double percent = ((double) progress / total) * 100;
                    log.debug(() -> filePath + " -> Transfer progress: " + percent + "% " + progress + " / " + total);
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future) {
                    if (future.isSuccess()) {
                        log.debug(() -> filePath + " -> Transfer complete: " + dataSize);
                        ctx.close(); // 如果不是 keep-alive 可以选择关闭连接
                    } else {
                        log.error(() -> filePath + " -> Transfer failed:" + future.cause().getMessage());
                    }
                }
            });
            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (!isKeepAlive) {
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        } catch (IOException ex) {
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    String error = SecurityUtil.sanitizeCRLF("Failed to close file: " + file.getAbsolutePath());
                    log.error(error, e);
                }
            }
            Err err = new Err(BootErrorCode.NIO_UNEXPECTED_SERVICE_FAILURE, null, "Failed to send file: " + file.getName(), ex, "Failed to send file: " + file.getAbsolutePath());
            file = null;
            context.response(file, false).error(err).status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            return sendResponse(ctx, isKeepAlive, context, errorAuditor, processorSettings);
        }
        return fileLength;
    }

    public static File buildErrorFile(final SessionContext sessionContext) {
        HttpResponseStatus status = sessionContext.status();
        int errorCode = status.code();
        boolean isDownloadMode = sessionContext.isDownloadMode();
        String errorFileName = errorCode + (isDownloadMode ? ".txt" : ".html");
        final NioConfig nioCfg = NioConfig.cfg;
        String errorPageFolderName = nioCfg.getErrorPageFolderName();
        File errorFile;
        if (StringUtils.isBlank(errorPageFolderName)) {
            errorFile = new File(nioCfg.getDocrootDir() + File.separator + errorFileName).getAbsoluteFile();
        } else {
            errorFile = new File(nioCfg.getDocrootDir() + File.separator + errorPageFolderName + File.separator + errorFileName).getAbsoluteFile();
        }
        if (!errorFile.exists()) {
            errorFile.getParentFile().mkdirs();
            String title = BackOffice.agent.getVersionShort();
            String errorDesc = status.reasonPhrase();
            StringBuilder sb = new StringBuilder();
            Path errorFilePath = errorFile.getAbsoluteFile().toPath();
            try (InputStream ioStream = sessionContext.getClass()
                    .getClassLoader()
                    .getResourceAsStream(ApplicationUtil.RESOURCE_PATH + "HttpErrorTemplate" + (isDownloadMode ? ".txt" : ".html")); InputStreamReader isr = new InputStreamReader(ioStream); BufferedReader br = new BufferedReader(isr);) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append(BootConstants.BR);
                }
                String errorFileContent = sb.toString().replace("${title}", title).replace("${code}", "" + errorCode).replace("${desc}", errorDesc);
                //errorFileContent = errorFileContent..replace("${title}", title);
                Files.writeString(errorFilePath, errorFileContent);
            } catch (IOException ex) {
                String message = title + ": errCode=" + errorCode + ", desc=" + errorDesc;
                Err e = new Err(BootErrorCode.FILE_NOT_FOUND, null, null, ex, "Failed to generate error page:" + errorFile.getName() + ", " + message);
                sessionContext.error(e);
            }
        }
        return errorFile;
    }

    public static final SimpleLocalCache<String, File> WebResourceCache = new SimpleLocalCacheImpl<>();

    public static void sendWebResource(final ServiceRequest request, final SessionContext response) throws IOException {
        String httpRequestPath = request.getHttpRequestPath();
        sendWebResource(httpRequestPath, response);
    }

    public static void sendWebResource(final String httpRequestPath, final SessionContext context) throws IOException {
        HttpHeaders headers = context.requestHeaders();
        if (headers != null) {
            String accept = headers.get(HttpHeaderNames.ACCEPT);
            if (accept != null) {
                accept = accept.toLowerCase();
                if (!accept.contains("html") && !accept.contains("web") && !accept.contains("image") && (accept.contains("json") || accept.contains("xml"))) {
                    var error = new Err(BootErrorCode.NIO_REQUEST_BAD_HEADER, null, "Client request header expect " + HttpHeaderNames.ACCEPT + "=" + accept + ", but request a web resource", null);
                    context.error(error).status(HttpResponseStatus.NOT_FOUND);
                    return;
                }
            }
        }
        File webResourceFile = WebResourceCache.get(httpRequestPath);
        if (webResourceFile == null) {
            webResourceFile = new File(NioConfig.cfg.getDocrootDir(), httpRequestPath);// CWE-73 False Positive
            String requestedPath = webResourceFile.getCanonicalPath();
            // CWE-73 False Positive prove
            if (!requestedPath.startsWith(NioConfig.cfg.getDocrootDir())) {
                Err e = new Err(BootErrorCode.FILE_NOT_ACCESSABLE, null, null, null, "Invalid request path: " + httpRequestPath);
                context.status(HttpResponseStatus.FORBIDDEN).error(e);
                return;
            }

            WebResourceCache.put(httpRequestPath, webResourceFile, BootConstants.WEB_RESOURCE_TTL_MS);
        }
        context.response(webResourceFile, false).level(Level.TRACE);
    }

    public static String getFileContentType(File file) {
        String mimeType;
        try {
            Tika tika = new Tika();
            mimeType = tika.detect(file);
        } catch (IOException ex) {
            MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
            mimeType = mimeTypesMap.getContentType(file.getPath());
            if (log.isWarnEnabled()) {
                String error = SecurityUtil.sanitizeCRLF("Failed to get MIME type from " + file.getAbsolutePath() + ": " + ex.getMessage());
                log.warn(error);
            }
        }
        return mimeType;
    }

    protected static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

    public static String getHttpPostBodyString(FullHttpRequest fullHttpRequest) {
        ByteBuf buf = fullHttpRequest.content();
        String jsonStr = buf.toString(io.netty.util.CharsetUtil.UTF_8);
        //buf.release();
        log.debug(() -> "\n" + fullHttpRequest.uri() + "\n" + jsonStr);
        return jsonStr;
    }

    public static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            return value;
        }
    }

    public static void onExceptionCaught(ChannelHandlerContext ctx, Throwable ex, Logger logger) {
        NioConfig nioCfg = NioConfig.cfg;
        String error = GeoIpUtil.callerAddressFilter(ctx.channel().remoteAddress(), nioCfg.getCallerAddressFilterWhitelist(), nioCfg.getCallerAddressFilterBlacklist(), nioCfg.getCallerAddressFilterOption());
        if (BootConstants.isDebugMode() || error == null) {
            if (ex instanceof DecoderException) {
                logger.warn(ctx.channel().remoteAddress() + ": " + ex);
            } else {
                logger.warn(ctx.channel().remoteAddress() + ": " + ex, ex);
            }
        }
        if (ex instanceof OutOfMemoryError || error != null) {
            ctx.close();
        }
    }

}
//        List<Integer> failed = rdlList.keySet()
//                .stream()
//                .filter(k -> rdlList.get(k) == null)
//                .sorted()
//                .collect(Collectors.toList());
//        log.error(() -> "RDL Signon failed: " + failed);
//
//        Map<Integer, String> success = rdlList.entrySet()
//                .stream()
//                .filter(e -> e.getValue() != null)
//                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
