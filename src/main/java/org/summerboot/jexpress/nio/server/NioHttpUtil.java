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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.AsciiString;
import jakarta.activation.MimetypesFileTypeMap;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.Tika;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.integration.cache.SimpleLocalCache;
import org.summerboot.jexpress.integration.cache.SimpleLocalCacheImpl;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ProcessorSettings;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.nio.server.domain.ServiceRequest;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.TimeUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
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
    public static final String HTTP_HEADER_AUTH_TOKEN = "Authorization";// "X-Auth-Token";// "X_Authorization"; //RFC 7235, sec. 4.2
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

    private static void sendRedirect(ChannelHandlerContext ctx, String newUri, HttpResponseStatus status) {
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);//HttpResponseStatus.FOUND, HttpResponseStatus.PERMANENT_REDIRECT : HttpResponseStatus.TEMPORARY_REDIRECT
        resp.headers().set(HttpHeaderNames.LOCATION, newUri);
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }


    public static long sendResponse(ChannelHandlerContext ctx, boolean isKeepAlive, final ServiceContext serviceContext, final ErrorAuditor errorAuditor, final ProcessorSettings processorSettings) {
        String headerKey_reference;
        String headerKey_serverTimestamp;
        if (processorSettings == null) {
            headerKey_reference = BootConstant.RESPONSE_HEADER_KEY_REF;
            headerKey_serverTimestamp = BootConstant.RESPONSE_HEADER_KEY_TS;
        } else {
            headerKey_reference = processorSettings.getHttpServiceResponseHeaderName_Reference();
            headerKey_serverTimestamp = processorSettings.getHttpServiceResponseHeaderName_ServerTimestamp();
        }
        serviceContext.responseHeader(headerKey_reference, serviceContext.txId());
        serviceContext.responseHeader(headerKey_serverTimestamp, OffsetDateTime.now().format(TimeUtil.ISO_ZONED_DATE_TIME3));
        final HttpResponseStatus status = serviceContext.status();

        if (serviceContext.file() != null) {
            return sendFile(ctx, isKeepAlive, serviceContext, errorAuditor, processorSettings);
        }
        if (serviceContext.redirect() != null) {
            sendRedirect(ctx, serviceContext.redirect(), status);
            return 0;
        }

        boolean hasErrorContent = StringUtils.isEmpty(serviceContext.txt()) && status.code() >= 400;
        if (hasErrorContent) {
            if (serviceContext.error() == null) {
                serviceContext.error(null);
            }
            String clientAcceptContentType = serviceContext.clientAcceptContentType();
            String errorResponse;
            if (clientAcceptContentType != null && clientAcceptContentType.contains("xml")) {
                errorResponse = serviceContext.error().toXML();
                serviceContext.contentType(MediaType.APPLICATION_XML);
            } else {
                errorResponse = serviceContext.error().toJson();
                serviceContext.contentType(MediaType.APPLICATION_JSON);
            }
            if (errorAuditor != null) {
                errorResponse = errorAuditor.beforeSendingError(errorResponse);
            }
            serviceContext.txt(errorResponse);
        }

        if (HttpResponseStatus.OK.equals(status) && serviceContext.autoConvertBlank200To204() && StringUtils.isEmpty(serviceContext.txt())) {
            serviceContext.status(HttpResponseStatus.NO_CONTENT);
        }
        return sendText(ctx, isKeepAlive, serviceContext.responseHeaders(), serviceContext.status(), serviceContext.txt(), serviceContext.contentType(), serviceContext.charsetName(), true, serviceContext.responseEncoder());
    }

    protected static final String DEFAULT_CHARSET = "UTF-8";

    protected static long sendText(ChannelHandlerContext ctx, boolean isKeepAlive, HttpHeaders serviceHeaders, HttpResponseStatus status, String content, String contentType, String charsetName, boolean flush, ResponseEncoder responseEncoder) {
        if (content == null) {
            content = "";
        }
        if (responseEncoder != null) {
            content = responseEncoder.encode(content);
        }
        //FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(content.getBytes(CharsetUtil.UTF_8)));
        byte[] contentBytes;
        if (charsetName == null) {
            contentBytes = content.getBytes(StandardCharsets.UTF_8);
            charsetName = DEFAULT_CHARSET;
        } else {
            try {
                contentBytes = content.getBytes(charsetName);
            } catch (UnsupportedEncodingException ex) {
//                String error = "Unsupported Header (Accept-Charset: " + charsetName + "): " + ex.getMessage();
//                contentBytes = error.getBytes(StandardCharsets.UTF_8);
//                status = HttpResponseStatus.NOT_ACCEPTABLE;
                if (log.isWarnEnabled()) {
                    String error = SecurityUtil.sanitizeCRLF("Unsupported Header (Accept-Charset: " + charsetName + "): " + ex.getMessage());
                    log.warn(error);
                }
                contentBytes = content.getBytes(StandardCharsets.UTF_8);
                charsetName = DEFAULT_CHARSET;
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
        int contentLength = resp.content().readableBytes();
        h.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(contentLength));

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
        return contentLength;
    }

    private static long sendFile(ChannelHandlerContext ctx, boolean isKeepAlive, final ServiceContext serviceContext, final ErrorAuditor errorAuditor, final ProcessorSettings processorSettings) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, serviceContext.status());
        HttpHeaders h = response.headers();
        h.set(serviceContext.responseHeaders());
        long fileLength = -1;
        RandomAccessFile randomAccessFile = null;
        File file = serviceContext.file();
        if (!SecurityUtil.precheckFile(file, serviceContext)) {
            file = buildErrorFile(serviceContext);
            serviceContext.file(file, false);
            return sendResponse(ctx, isKeepAlive, serviceContext, errorAuditor, processorSettings);
        }

        serviceContext.memo("sendFile", file.getAbsolutePath());
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
            ChannelFuture sendFileFuture = ctx.write(new ChunkedFile(randomAccessFile, 0, fileLength, 8192), ctx.newProgressivePromise());
            sendFileFuture.addListener(new ChannelProgressiveFutureListener() { // CWE-404 False Positive prove
                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                    if (total < 0) { // total unknown
                        log.error(filePath + " -> Transfer progress: " + progress);
                    } else {
                        log.debug(() -> filePath + " -> Transfer progress: " + progress + " / " + total);
                    }
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future) throws Exception {
                    log.debug(() -> filePath + " -> Transfer complete.");
                    raf.close();
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
            Err err = new Err(BootErrorCode.NIO_UNEXPECTED_SERVICE_FAILURE, null, null, ex, "Failed to send file: " + file.getAbsolutePath());
            file = null;
            serviceContext.file(file, false).error(err).status(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            return sendResponse(ctx, isKeepAlive, serviceContext, errorAuditor, processorSettings);
        }
        return fileLength;
    }

    public static File buildErrorFile(final ServiceContext serviceContext) {
        HttpResponseStatus status = serviceContext.status();
        int errorCode = status.code();
        boolean isDownloadMode = serviceContext.isDownloadMode();
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
            try (InputStream ioStream = serviceContext.getClass()
                    .getClassLoader()
                    .getResourceAsStream(ApplicationUtil.RESOURCE_PATH + "HttpErrorTemplate" + (isDownloadMode ? ".txt" : ".html")); InputStreamReader isr = new InputStreamReader(ioStream); BufferedReader br = new BufferedReader(isr);) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append(BootConstant.BR);
                }
                String errorFileContent = sb.toString().replace("${title}", title).replace("${code}", "" + errorCode).replace("${desc}", errorDesc);
                //errorFileContent = errorFileContent..replace("${title}", title);
                Files.writeString(errorFilePath, errorFileContent);
            } catch (IOException ex) {
                String message = title + ": errCode=" + errorCode + ", desc=" + errorDesc;
                Err e = new Err(BootErrorCode.FILE_NOT_FOUND, null, null, ex, "Failed to generate error page:" + errorFile.getName() + ", " + message);
                serviceContext.error(e);
            }
        }
        return errorFile;
    }

    public static final SimpleLocalCache<String, File> WebResourceCache = new SimpleLocalCacheImpl();

    public static void sendWebResource(final ServiceRequest request, final ServiceContext response) throws IOException {
        String httpRequestPath = request.getHttpRequestPath();
        sendWebResource(httpRequestPath, response);
    }

    public static void sendWebResource(final String httpRequestPath, final ServiceContext context) throws IOException {
        HttpHeaders headers = context.requestHeaders();
        if (headers != null) {
            String accept = headers.get(HttpHeaderNames.ACCEPT);
            if (accept != null) {
                accept = accept.toLowerCase();
                if (!accept.contains("html") && !accept.contains("web") && !accept.contains("image") && (accept.contains("json") || accept.contains("xml"))) {
                    var error = new Err(BootErrorCode.NIO_REQUEST_BAD_HEADER, null, null, null, "Client expect " + accept + ", but request a web resource");
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

            WebResourceCache.put(httpRequestPath, webResourceFile, BootConstant.WEB_RESOURCE_TTL_MS);
        }
        context.file(webResourceFile, false).level(Level.TRACE);
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
