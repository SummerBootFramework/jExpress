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
package org.summerboot.jexpress.nio.server.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.summerboot.jexpress.boot.BackOffice;
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.nio.server.NioConfig;
import org.summerboot.jexpress.nio.server.NioHttpUtil;
import org.summerboot.jexpress.nio.server.ResponseEncoder;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.ApplicationUtil;
import org.summerboot.jexpress.util.BeanUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ServiceContext {

    //protected ChannelHandlerContext ctx;
    protected final SocketAddress localIP;
    protected final SocketAddress remoteIP;
    protected final HttpMethod requesMethod;
    protected final String requesURI;
    protected final HttpHeaders requestHeaders;
    protected final String requestBody;
    protected final String txId;
    protected final long hit;
    protected final long startTs;
    protected final OffsetDateTime startDateTime;
    protected Caller caller;
    protected String callerId;

    //  1.1 status
    protected HttpResponseStatus status = HttpResponseStatus.OK;
    protected boolean autoConvertBlank200To204 = true;
    // 1.2 responseHeader
    protected HttpHeaders responseHeaders;
    protected ResponseEncoder responseEncoder = null;
    // 1.3 content type    
    protected String contentType;// = MediaType.APPLICATION_JSON;
    protected String clientAcceptContentType;
    protected String charsetName;
    // 1.4 data
    protected byte[] data;
    protected String txt = "";
    protected File file;
    protected boolean downloadMode = true;
    protected String redirect;
    protected final List<POI> poi = new ArrayList<>();
    protected List<Memo> memo;

    // Session attributes
    protected Map<Object, Object> sessionAttributes;

    // 2.1 error
//    protected int errorCode;
//    protected String errorTag;
//    protected Throwable cause;
    protected ServiceError serviceError;
    protected Throwable cause;
    // 2.2 logging control
    protected Level level = Level.INFO;
    protected boolean logRequestHeader = true;
    protected boolean logResponseHeader = true;
    protected boolean logRequestBody = true;
    protected boolean logResponseBody = true;

    public static ServiceContext build(long hit) {
        return build(BootConstant.APP_ID + "-" + hit, hit);
    }

    public static ServiceContext build(String txId, long hit) {
        return new ServiceContext(null, txId, hit, System.currentTimeMillis(), null, null, null, null);
    }

    public static ServiceContext build(ChannelHandlerContext ctx, String txId, long hit, long startTs, HttpHeaders requestHeaders, HttpMethod requesMethod, String requesURI, String requestBody) {
        return new ServiceContext(ctx, txId, hit, startTs, requestHeaders, requesMethod, requesURI, requestBody);
    }

    @Override
    public String toString() {
        //return "ServiceContext{" + "status=" + status + ", responseHeader=" + responseHeader + ", contentType=" + contentType + ", data=" + data + ", txt=" + txt + ", errorCode=" + errorCode + ", errorTag=" + errorTag + ", cause=" + cause + ", level=" + level + ", logReqHeader=" + logRequestHeader + ", logRespHeader=" + logResponseHeader + ", logReqContent=" + logRequestBody + ", logRespContent=" + logResponseBody + '}';
        return "ServiceContext{" + "status=" + status + ", responseHeaders=" + responseHeaders + ", contentType=" + contentType + ", data=" + data + ", txt=" + txt + ", errors=" + serviceError + ", level=" + level + ", logReqHeader=" + logRequestHeader + ", logRespHeader=" + logResponseHeader + ", logReqContent=" + logRequestBody + ", logRespContent=" + logResponseBody + '}';
    }

    protected ServiceContext(ChannelHandlerContext ctx, String txId, long hit, long startTs, HttpHeaders requestHeaders, HttpMethod requesMethod, String requesURI, String requestBody) {
        if (ctx != null && ctx.channel() != null) {
            this.localIP = ctx.channel().localAddress();
            this.remoteIP = ctx.channel().remoteAddress();
        } else {
            this.localIP = null;
            this.remoteIP = null;
        }
        this.txId = txId;
        this.hit = hit;
        this.startTs = startTs;
        this.startDateTime = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(startTs), java.time.ZoneId.systemDefault());
        this.requestHeaders = requestHeaders;
        this.requesMethod = requesMethod;
        this.requesURI = requesURI;
        this.requestBody = requestBody;
        poi.add(new POI(BootPOI.SERVICE_BEGIN));
    }

//    public void clear() {
//        if (poi != null) {
//            poi.clear();
//        }
//        if (memo != null) {
//            memo.clear();
//        }
//        if (attributes != null) {
//            attributes.clear();
//        }
//    }

    /**
     * This method always returns a HttpSession object. It returns the session object attached with the request, if the request has no session attached, then it creates a new session and return it.
     *
     * @return
     */
    public Map<Object, Object> session() {
        return session(true);
    }

    /**
     * This method returns HttpSession object if request has session else it returns null.
     *
     * @param create
     * @return
     */
    public Map<Object, Object> session(boolean create) {
        if (sessionAttributes == null && create) {
            sessionAttributes = new HashMap();
        }
        return sessionAttributes;
    }

    /**
     * get attribute value by kay
     *
     * @param key
     * @return
     */
    public <T extends Object> T sessionAttribute(Object key) {
        return sessionAttributes == null ? null : (T) sessionAttributes.get(key);
    }

    /**
     * set or remove attribute value, or clear all attributes when both key and value are null
     *
     * @param key
     * @param value remove key-value if value is null, otherwise add key-value
     * @return current ServiceContext instance
     */
    public ServiceContext sessionAttribute(Object key, Object value) {
        if (sessionAttributes == null) {
            sessionAttributes = new HashMap();
        }
        if (key == null && value == null) {
            sessionAttributes.clear();
            return this;
        }
        if (value == null) {
            sessionAttributes.remove(key);
        } else {
            sessionAttributes.put(key, value);
        }
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public SocketAddress localIP() {
        return this.localIP;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public SocketAddress remoteIP() {
        return this.remoteIP;
    }

    public long startTimestamp() {
        return startTs;
    }

    public OffsetDateTime startDateTime() {
        return startDateTime;
    }

    public ServiceContext resetResponseData() {
        // 1. data
        txt = "";
        file = null;
        redirect = null;
        data = null;

        // 2. error
        serviceError = null;
        cause = null;
        status = HttpResponseStatus.OK;
        level(Level.INFO);
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public String txId() {
        return txId;
    }

    public long hit() {
        return hit;
    }

    public HttpMethod method() {
        return requesMethod;
    }

    public String uri() {
        return requesURI;
    }

    public String requestBody() {
        return requestBody;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public HttpResponseStatus status() {
        return status;
    }

    public ServiceContext status(HttpResponseStatus status) {
        return status(status, null);
    }

    public ServiceContext status(HttpResponseStatus status, Boolean autoConvertBlank200To204) {
        this.status = status;
        if (autoConvertBlank200To204 != null) {
            this.autoConvertBlank200To204 = autoConvertBlank200To204;
        }
        return this;
    }

    public HttpHeaders requestHeaders() {
        return requestHeaders;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public HttpHeaders responseHeaders() {
        return responseHeaders;
    }

    public ServiceContext responseHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return this;
        }
        if (this.responseHeaders == null) {
            this.responseHeaders = new DefaultHttpHeaders();
        }
        this.responseHeaders.set(headers);
        return this;
    }

    //        public Response addHeader(String key, Object value) {
//            if (StringUtils.isBlank(key) || value == null) {
//                return this;
//            }
//            if (responseHeader == null) {
//                responseHeader = new DefaultHttpHeaders(true);
//            }
//            responseHeader.add(key, value);
//            return this;
//        }
    public ServiceContext responseHeader(String key, Object value) {
        if (StringUtils.isBlank(key)) {
            return this;
        }
        if (responseHeaders == null) {
            responseHeaders = new DefaultHttpHeaders();
        }
        if (value == null) {
            responseHeaders.remove(key);
        } else {
            responseHeaders.set(key, value);
        }
        return this;
    }

    //        public Response addHeaders(String key, Iterable<?> values) {
//            if (StringUtils.isBlank(key) || values == null) {
//                return this;
//            }
//            if (responseHeader == null) {
//                responseHeader = new DefaultHttpHeaders(true);
//            }
//            responseHeader.add(key, values);
//            return this;
//        }
    public ServiceContext responseHeader(String key, Iterable<?> values) {
        if (StringUtils.isBlank(key)) {
            return this;
        }
        if (responseHeaders == null) {
            responseHeaders = new DefaultHttpHeaders();
        }
        if (values == null) {
            responseHeaders.remove(key);
        } else {
            responseHeaders.set(key, values);
        }
        return this;
    }

    public ServiceContext responseHeaders(Map<String, Iterable<?>> hs) {
        if (hs == null) {
            return this;
        }
        if (responseHeaders == null) {
            responseHeaders = new DefaultHttpHeaders();
        }
        hs.keySet().stream().filter((key) -> (StringUtils.isNotBlank(key))).forEachOrdered((key) -> {
            Iterable<?> values = hs.get(key);
            if (values == null) {
                responseHeaders.remove(key);
            } else {
                responseHeaders.set(key, values);
            }
        });
        return this;
    }

    public ResponseEncoder responseEncoder() {
        return responseEncoder;
    }

    public ServiceContext responseEncoder(ResponseEncoder responseEncoder) {
        this.responseEncoder = responseEncoder;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public String contentType() {
        return contentType;
    }

    public ServiceContext contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public String clientAcceptContentType() {
        return clientAcceptContentType;
    }

    public ServiceContext clientAcceptContentType(String clientAcceptContentType) {
        this.clientAcceptContentType = clientAcceptContentType;
        return this;
    }

    //    public ServiceContext contentTypeTry(String contentType) {
//        if (contentType != null) {
//            this.contentType = contentType;
//        }
//        return this;
//    }
    public String charsetName() {
        return charsetName;
    }

    public ServiceContext charsetName(String charsetName) {
        this.charsetName = charsetName;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public String redirect() {
        return this.redirect;
    }

    public ServiceContext redirect(String redirect) {
        return redirect(redirect, HttpResponseStatus.TEMPORARY_REDIRECT);//MOVED_PERMANENTLY 301, FOUND 302, TEMPORARY_REDIRECT 307, PERMANENT_REDIRECT 308
    }

    public ServiceContext redirect(String redirect, HttpResponseStatus status) {
        this.redirect = redirect;
        this.txt = null;
        this.file = null;
        this.status = status;
        responseHeader(HttpHeaderNames.LOCATION.toString(), redirect);
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public String txt() {
        return txt;
    }

    public ServiceContext txt(String txt) {
        this.txt = txt;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public byte[] data() {
        return data;
    }

    public ServiceContext data(byte[] data) {
        this.data = data;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public File file() {
        return file;
    }

    public boolean isDownloadMode() {
        return downloadMode;
    }

    public ServiceContext downloadMode(boolean downloadMode) {
        this.downloadMode = downloadMode;
        return this;
    }

    public boolean precheckFolder(File folder) {
        this.file = null;
        String filePath = folder.getAbsolutePath();
        String realPath;
        try {
            realPath = file.getAbsoluteFile().toPath().normalize().toString();
        } catch (Throwable ex) {
            Err e = new Err(BootErrorCode.NIO_REQUEST_BAD_DOWNLOAD, null, null, ex, "Invalid file path: " + filePath);
            this.status(HttpResponseStatus.BAD_REQUEST).error(e);
            return false;
        }
        memo("folder.view", filePath);

        if (!folder.exists()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_FOUND, null, null, null, "File not exists: " + filePath);
            this.status(HttpResponseStatus.NOT_FOUND).error(e);
            return false;
        }

        if (!NioHttpUtil.sanitizePath(filePath) || !filePath.equals(realPath)
                || !folder.isDirectory() || folder.isFile()
                || folder.isHidden() || !folder.canRead()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_ACCESSABLE, null, null, null, "Malicious file reqeust: " + filePath);
            // 2. build JSON response with same app error code, and keep the default INFO log level.
            this.status(HttpResponseStatus.FORBIDDEN).error(e);
            return false;
        }
        return true;
    }

    public boolean precheckFile(File file, boolean isDownloadMode) {
        this.file = null;
        String filePath = file.getAbsolutePath();
        memo("file." + (isDownloadMode ? "download" : "view"), filePath);
        String realPath;
        try {
            realPath = file.getAbsoluteFile().toPath().normalize().toString();
        } catch (Throwable ex) {
            Err e = new Err(BootErrorCode.NIO_REQUEST_BAD_DOWNLOAD, null, null, ex, "Invalid file path: " + filePath);
            this.status(HttpResponseStatus.BAD_REQUEST).error(e);
            return false;
        }

        if (!file.exists()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_FOUND, null, null, null, "File not exists: " + filePath);
            this.status(HttpResponseStatus.NOT_FOUND).error(e);
            return false;
        }

        if (!NioHttpUtil.sanitizePath(filePath) || !filePath.equals(realPath)
                || file.isDirectory() || !file.isFile()
                || file.isHidden() || !file.canRead()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_ACCESSABLE, null, null, null, "Malicious file reqeust: " + filePath);
            // 2. build JSON response with same app error code, and keep the default INFO log level.
            this.status(HttpResponseStatus.FORBIDDEN).error(e);
            return false;
        }

        return true;
    }

    //    protected static final List<Integer> ERRPR_PAGES = new ArrayList();
//
//    static {
//        ERRPR_PAGES.add(HttpResponseStatus.UNAUTHORIZED.code());
//        ERRPR_PAGES.add(HttpResponseStatus.FORBIDDEN.code());
//        ERRPR_PAGES.add(HttpResponseStatus.NOT_FOUND.code());
//    }
//
//    public ServiceContext visualizeError() {
//        if (ERRPR_PAGES.contains(status.code())) {
//            String errorFileName = status.code() + ".html";
//            File errorFile = new File(HttpClientConfig.CFG.getDocroot() + File.separator + HttpClientConfig.CFG.getWebResources()
//                     + File.separator + errorFileName).getAbsoluteFile();
//            file(errorFile, false);
//        }
//        return this;
//    }
    protected File buildErrorFile(HttpResponseStatus status, boolean isDownloadMode) {
        int errorCode = status.code();
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
            try (InputStream ioStream = this.getClass()
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
                this.error(e);
            }
        }
        return errorFile;
    }

    public ServiceContext file(String fileName, boolean isDownloadMode) {
        String targetFileName = NioConfig.cfg.getDocrootDir() + File.separator + fileName;
        targetFileName = targetFileName.replace('/', File.separatorChar);
        File targetFile = new File(targetFileName).getAbsoluteFile();
        return this.file(targetFile, isDownloadMode);
    }

    public ServiceContext file(File file, boolean isDownloadMode) {
        this.downloadMode = isDownloadMode;
        if (!precheckFile(file, downloadMode)) {
            file = buildErrorFile(status, downloadMode);
        }
        this.txt = null;
        this.redirect = null;
        this.file = file;
        this.contentType = NioHttpUtil.getFileContentType(file);
//        if (!downloadMode) {
//            serviceError = null;
//        }

        if (responseHeaders == null) {
            responseHeaders = new DefaultHttpHeaders();
        }
        long fileLength = file.length();
        if (fileLength > Integer.MAX_VALUE) {
            responseHeaders.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(fileLength));
        } else {
            responseHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, (int) fileLength);
        }
        responseHeaders.set(HttpHeaderNames.CONTENT_TYPE, contentType);
        if (downloadMode) {
            String fileName = file.getName();
            try {
                fileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            } catch (UnsupportedEncodingException ex) {
            }
            responseHeaders.set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment;filename=" + fileName + ";filename*=UTF-8''" + fileName);
        }
        return this;
    }

    public ServiceContext content(Object ret) throws JsonProcessingException {
        if (ret == null) {
            return this;
        }
        if (ret instanceof File) {
            this.file((File) ret, true);
        } else {
            String responseContentType;
            //1. calculate responseContentType
            if (clientAcceptContentType == null) {// client not specified
                responseContentType = MediaType.APPLICATION_JSON;
            } else if (clientAcceptContentType.contains("json")) {
                responseContentType = MediaType.APPLICATION_JSON;
            } else if (clientAcceptContentType.contains("xml")) {
                responseContentType = MediaType.APPLICATION_XML;
            } else if (clientAcceptContentType.contains("txt")) {
                responseContentType = MediaType.TEXT_HTML;
            } else {
                responseContentType = MediaType.APPLICATION_JSON;
            }

            //2. set content and contentType
            if (ret instanceof String) {
                this.txt((String) ret);
            } else {
                switch (responseContentType) {
                    case MediaType.APPLICATION_JSON:
                        this.txt(BeanUtil.toJson(ret));
                        break;
                    case MediaType.APPLICATION_XML:
                    case MediaType.TEXT_XML:
                        this.txt(BeanUtil.toXML(ret));
                        break;
                    case MediaType.TEXT_HTML:
                    case MediaType.TEXT_PLAIN:
                        this.txt(ret.toString());
                        break;
                }
            }
            //3. update content type
            if (this.contentType() == null) {
                this.contentType(responseContentType);
            }
        }

        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public <T extends Caller> T caller() {
        return (T) caller;
    }

    public <T extends Caller> ServiceContext caller(T caller) {
        this.caller = caller;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public String callerId() {
        return callerId;
    }

    public ServiceContext callerId(String callerId) {
        this.callerId = callerId;
        return this;
    }

//    public int errorCode() {
//        return errorCode;
//    }
//
//    public ServiceContext errorCode(int errorCode) {
//        this.errorCode = errorCode;
//        return this;
//    }
//
//    public String errorTag() {
//        return errorTag;
//    }
//
//    public ServiceContext errorTag(String errorTag) {
//        this.errorTag = errorTag;
//        return this;
//    }
//
//    public Throwable cause() {
//        return cause;
//    }
//
//    public ServiceContext cause(Throwable ex) {
//        this.cause = ex;
//        if (ex == null) {
//            level = Level.INFO;
//        } else {
//            level = Level.ERROR;
//        }
//        return this;
//    }

    public boolean hasError() {
        return serviceError != null && serviceError.getErrors() != null && !serviceError.getErrors().isEmpty();
    }

    public List<Err> errors() {
        return hasError() ? serviceError.getErrors() : null;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public ServiceError error() {
//        if (serviceError == null || serviceError.getErrors() == null || serviceError.getErrors().isEmpty()) {
//            return null;
//        }
        return serviceError;
    }

    /**
     * Set error
     *
     * @param error
     * @return
     */
    public ServiceContext error(Err error) {
        if (serviceError == null) {
            serviceError = new ServiceError(txId);
        }
        if (error == null) {
            return this;
        }
        serviceError.addError(error);
        Throwable t = error.getCause();
        if (t != null) {
            cause = t;
        }
        // set log level
        if (error.getCause() != null) {
            level(Level.ERROR);
        }
        return this;
    }

    /**
     * Clear or set errors
     *
     * @param es
     * @return
     */
    public ServiceContext errors(Collection<Err> es) {
        if (es == null || es.isEmpty()) {
            if (serviceError != null && serviceError.getErrors() != null) {
                serviceError.getErrors().clear();
                serviceError = null;
            }
            return this;
        }
        if (serviceError == null) {
            serviceError = new ServiceError(txId);
        }
        serviceError.addErrors(es);
        for (Err e : es) {
            Throwable t = e.getCause();
            if (t != null) {
                cause = t;
            }
            if (cause != null) {
                level(Level.ERROR);
                //break;
            }
        }
        return this;
    }

    public ServiceContext cause(Throwable cause) {
        this.cause = cause;
        if (cause != null) {
            Throwable root = ExceptionUtils.getRootCause(cause);
            if (root == null || root.equals(cause)) {
                if (level().isLessSpecificThan(Level.WARN)) {
                    level(Level.WARN);
                }
            } else {
                if (level().isLessSpecificThan(Level.ERROR)) {
                    level(Level.ERROR);
                }
            }
        }
        return this;
    }

    public Throwable cause() {
        return cause;
    }

    // 2.2 logging control
    /*
    OFF=0
    FATAL=100
    ERROR=200
    WARN=300
    INFO=400
    DEBUG=500
    TRACE=600
    ALL=2147483647
     */
    public Level level() {
        return level;
    }

    public ServiceContext level(Level level) {
        this.level = level;
        return this;
    }

    public ServiceContext logRequestHeader(boolean enabled) {
        this.logRequestHeader = enabled;
        return this;
    }

    public boolean logRequestHeader() {
        return logRequestHeader;
    }

    public ServiceContext logRequestBody(boolean enabled) {
        this.logRequestBody = enabled;
        return this;
    }

    public boolean logRequestBody() {
        return logRequestBody;
    }

    public ServiceContext logResponseHeader(boolean enabled) {
        this.logResponseHeader = enabled;
        return this;
    }

    public boolean logResponseHeader() {
        return logResponseHeader;
    }

    public ServiceContext logResponseBody(boolean enabled) {
        this.logResponseBody = enabled;
        return this;
    }

    public boolean logResponseBody() {
        return logResponseBody;
    }

    public ServiceContext poi(String marker) {
//        if (poi == null) {
//            //poi = new LinkedHashMap();
//            poi = new ArrayList();
//        }
        //poi.put(marker, System.currentTimeMillis());
        poi.add(new POI(marker));
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public List<POI> poi() {
        return poi;
    }

    public ServiceContext memo(String desc) {
        return this.memo(null, desc);
    }

    public ServiceContext memo(String id, String desc) {
        if (memo == null) {
            memo = new ArrayList();
        }
        memo.add(new Memo(id, desc));
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public List<Memo> memo() {
        return memo;
    }

    public boolean autoConvertBlank200To204() {
        return autoConvertBlank200To204;
    }

    public static class POI {

        public final String name;
        public final long ts = System.currentTimeMillis();

        public POI(String name) {
            this.name = name;
        }
    }

    public static class Memo {

        public final String id;
        public final String desc;

        public Memo(String id, String desc) {
            this.id = id;
            this.desc = desc;
        }

    }

    public StringBuilder report() {
        StringBuilder sb = new StringBuilder();
        reportPOI(sb);
        reportMemo(sb);
        reportError(sb);
        return sb;
    }

    public ServiceContext report(StringBuilder sb) {
        reportPOI(sb);
        reportMemo(sb);
        reportError(sb);
        return this;
    }

    public ServiceContext reportError(StringBuilder sb) {
        if (serviceError == null /*|| file == null*/) {// log error only for file request
            return this;
        }
//        if (file != null) {
//            sb.append("\n\n\tError: ");
//            sb.append(BeanUtil.toJson(serviceError.showRootCause(true), true, true));
//        } else {
//            List<Err> errors = serviceError.getErrors();
//            if (errors != null && errors.size() > 1) {
//                sb.append("\n\n\tExceptions: ");
//                for (var error : errors) {
//                    if (error.getCause() != null) {
//                        sb.append("\n\t ").append(error.getCause());
//                    }
//                }
//            }
//        }
        List<Err> errors = serviceError.getErrors();
        if (errors != null && !errors.isEmpty()) {
            sb.append("\n\n\tErrors: ");
            for (var error : errors) {
                sb.append("\n\t ").append(error.toStringEx(true));
            }
        }

        return this;
    }

    public ServiceContext reportMemo(StringBuilder sb) {
        if (memo == null || memo.isEmpty()) {
            //sb.append("\n\tMemo: n/a");
            return this;
        }
        sb.append("\n\n\tMemo: ");
        memo.forEach((m) -> {
            if (m.id == null || m.id.isEmpty()) {
                sb.append("\n\t\t").append(m.desc);
            } else {
                sb.append("\n\t\t").append(m.id).append(BootConstant.MEMO_DELIMITER).append(m.desc);
            }
        });
        return this;
    }

    public ServiceContext reportPOI(StringBuilder sb) {
        return reportPOI(null, sb);
    }

    public ServiceContext reportPOI(NioConfig cfg, StringBuilder sb) {
        if (poi == null || poi.isEmpty()) {
            sb.append("\n\tPOI: n/a");
            return this;
        }
        NioConfig.VerboseTargetPOIType filterType = cfg == null ? NioConfig.VerboseTargetPOIType.all : cfg.getFilterPOIType();
        sb.append("\n\tPOI.t0=").append(startDateTime).append(" ");
        switch (filterType) {
            case all:
                poi.forEach((p) -> {
                    sb.append(p.name).append("=").append(p.ts - startTs).append("ms, ");
                });
                break;
            case filter:
                Set<String> poiSet = cfg.getFilterPOISet();
                poi.stream().filter((p) -> (poiSet.contains(p.name))).forEachOrdered((p) -> {
                    sb.append(p.name).append("=").append(p.ts - startTs).append("ms, ");
                });
                break;
            case ignore:
                sb.append("off");
                break;
        }
        return this;
    }
}
