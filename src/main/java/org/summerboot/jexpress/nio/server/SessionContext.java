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
import org.summerboot.jexpress.boot.BootConstant;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ProcessorSettings;
import org.summerboot.jexpress.nio.server.domain.ServiceError;
import org.summerboot.jexpress.security.SecurityUtil;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.util.BeanUtil;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.net.URLEncoder;
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
public class SessionContext {

    //protected static final ScopedValue<SessionContext> SESSION_CONTEXT = ScopedValue.newInstance();

//    public static SessionContext get() {
//        return SESSION_CONTEXT.get();
//    }


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

    protected ProcessorSettings processorSettings;

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

    public static SessionContext build(long hit) {
        return build(BootConstant.APP_ID + "-" + hit, hit);
    }

    public static SessionContext build(String txId, long hit) {
        return new SessionContext(null, txId, hit, System.currentTimeMillis(), null, null, null, null);
    }

    public static SessionContext build(ChannelHandlerContext ctx, String txId, long hit, long startTs, HttpHeaders requestHeaders, HttpMethod requesMethod, String requesURI, String requestBody) {
        return new SessionContext(ctx, txId, hit, startTs, requestHeaders, requesMethod, requesURI, requestBody);
    }

    @Override
    public String toString() {
        //return "SessionContext{" + "status=" + status + ", responseHeader=" + responseHeader + ", contentType=" + contentType + ", data=" + data + ", txt=" + txt + ", errorCode=" + errorCode + ", errorTag=" + errorTag + ", cause=" + cause + ", level=" + level + ", logReqHeader=" + logRequestHeader + ", logRespHeader=" + logResponseHeader + ", logReqContent=" + logRequestBody + ", logRespContent=" + logResponseBody + '}';
        return "SessionContext{" + "status=" + status + ", responseHeaders=" + responseHeaders + ", contentType=" + contentType + ", data=" + data + ", txt=" + txt + ", errors=" + serviceError + ", level=" + level + ", logReqHeader=" + logRequestHeader + ", logRespHeader=" + logResponseHeader + ", logReqContent=" + logRequestBody + ", logRespContent=" + logResponseBody + '}';
    }

    protected SessionContext(ChannelHandlerContext ctx, String txId, long hit, long startTs, HttpHeaders requestHeaders, HttpMethod requesMethod, String requesURI, String requestBody) {
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

    public SessionContext(SocketAddress localIP, SocketAddress remoteIP, String txId, long hit, long startTs, HttpHeaders requestHeaders, HttpMethod requesMethod, String requesURI, String requestBody) {
        this.localIP = localIP;
        this.remoteIP = remoteIP;
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
     * @return current SessionContext instance
     */
    public SessionContext sessionAttribute(Object key, Object value) {
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

    public SessionContext resetResponseData() {
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

    public SessionContext status(HttpResponseStatus status) {
        return status(status, null);
    }

    public SessionContext status(HttpResponseStatus status, Boolean autoConvertBlank200To204) {
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

    public SessionContext responseHeaders(HttpHeaders headers) {
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
    public SessionContext responseHeader(String key, Object value) {
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
    public SessionContext responseHeader(String key, Iterable<?> values) {
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

    public SessionContext responseHeaders(Map<String, Iterable<?>> hs) {
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

    public SessionContext responseEncoder(ResponseEncoder responseEncoder) {
        this.responseEncoder = responseEncoder;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public String contentType() {
        return contentType;
    }

    public SessionContext contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public String clientAcceptContentType() {
        return clientAcceptContentType;
    }

    public SessionContext clientAcceptContentType(String clientAcceptContentType) {
        this.clientAcceptContentType = clientAcceptContentType;
        return this;
    }

    //    public SessionContext contentTypeTry(String contentType) {
//        if (contentType != null) {
//            this.contentType = contentType;
//        }
//        return this;
//    }
    public String charsetName() {
        return charsetName;
    }

    public SessionContext charsetName(String charsetName) {
        this.charsetName = charsetName;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public String redirect() {
        return this.redirect;
    }

    public SessionContext redirect(String redirect) {
        return redirect(redirect, HttpResponseStatus.TEMPORARY_REDIRECT);//MOVED_PERMANENTLY 301, FOUND 302, TEMPORARY_REDIRECT 307, PERMANENT_REDIRECT 308
    }

    public SessionContext redirect(String redirect, HttpResponseStatus status) {
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

    public SessionContext txt(String txt) {
        this.txt = txt;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public byte[] data() {
        return data;
    }

    public SessionContext data(byte[] data) {
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

    public SessionContext downloadMode(boolean downloadMode) {
        this.downloadMode = downloadMode;
        return this;
    }

    public SessionContext file(String fileName, boolean isDownloadMode) {
        String targetFileName = NioConfig.cfg.getDocrootDir() + File.separator + fileName;
        targetFileName = targetFileName.replace('/', File.separatorChar);
        File targetFile = new File(targetFileName).getAbsoluteFile();
        return this.file(targetFile, isDownloadMode);
    }

    public SessionContext file(File file, boolean isDownloadMode) {
        this.downloadMode = isDownloadMode;
        this.file = null;
        memo("file." + (isDownloadMode ? "download" : "view"), file.getAbsolutePath());
        if (!SecurityUtil.precheckFile(file, this)) {
            file = NioHttpUtil.buildErrorFile(this);
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

    public SessionContext content(Object ret) throws JsonProcessingException {
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

    public <T extends Caller> SessionContext caller(T caller) {
        this.caller = caller;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public String callerId() {
        return callerId;
    }

    public SessionContext callerId(String callerId) {
        this.callerId = callerId;
        return this;
    }

//    public int errorCode() {
//        return errorCode;
//    }
//
//    public SessionContext errorCode(int errorCode) {
//        this.errorCode = errorCode;
//        return this;
//    }
//
//    public String errorTag() {
//        return errorTag;
//    }
//
//    public SessionContext errorTag(String errorTag) {
//        this.errorTag = errorTag;
//        return this;
//    }
//
//    public Throwable cause() {
//        return cause;
//    }
//
//    public SessionContext cause(Throwable ex) {
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
    public SessionContext error(Err error) {
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
    public SessionContext errors(Collection<Err> es) {
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

    public SessionContext cause(Throwable cause) {
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

    public SessionContext level(Level level) {
        this.level = level;
        return this;
    }

    public SessionContext logRequestHeader(boolean enabled) {
        this.logRequestHeader = enabled;
        return this;
    }

    public boolean logRequestHeader() {
        return logRequestHeader;
    }

    public SessionContext logRequestBody(boolean enabled) {
        this.logRequestBody = enabled;
        return this;
    }

    public boolean logRequestBody() {
        return logRequestBody;
    }

    public SessionContext logResponseHeader(boolean enabled) {
        this.logResponseHeader = enabled;
        return this;
    }

    public boolean logResponseHeader() {
        return logResponseHeader;
    }

    public SessionContext logResponseBody(boolean enabled) {
        this.logResponseBody = enabled;
        return this;
    }

    public boolean logResponseBody() {
        return logResponseBody;
    }

    public ProcessorSettings processorSettings() {
        return processorSettings;
    }

    public SessionContext processorSettings(ProcessorSettings processorSettings) {
        this.processorSettings = processorSettings;
        return this;
    }

    public SessionContext poi(String marker) {
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

    public SessionContext memo(String desc) {
        return this.memo(null, desc);
    }

    public SessionContext memo(String id, String desc) {
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

    public SessionContext report(StringBuilder sb) {
        reportPOI(sb);
        reportMemo(sb);
        reportError(sb);
        return this;
    }

    public SessionContext reportError(StringBuilder sb) {
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

    public SessionContext reportMemo(StringBuilder sb) {
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

    public SessionContext reportPOI(StringBuilder sb) {
        return reportPOI(null, sb);
    }

    public SessionContext reportPOI(NioConfig cfg, StringBuilder sb) {
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
