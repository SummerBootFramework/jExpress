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

import org.summerboot.jexpress.nio.server.NioHttpUtil;
import org.summerboot.jexpress.security.auth.Caller;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.nio.server.NioConfig;
import java.util.Set;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ServiceContext {

    private static final NioConfig nioCfg = NioConfig.cfg;

    //private ChannelHandlerContext ctx;
    private final SocketAddress localIP;
    private final SocketAddress remoteIP;
    private final HttpMethod requesMethod;
    private final String requesURI;
    private final HttpHeaders requestHeaders;
    private final String requestBody;
    private final long hit;
    private final long startTs;
    private Caller caller;
    private String callerId;

    //  1.1 status
    private HttpResponseStatus status = HttpResponseStatus.OK;
    private boolean autoConvertBlank200To204 = true;
    // 1.2 responseHeader
    private HttpHeaders responseHeaders;
    // 1.3 content type    
    private String contentType;// = MediaType.APPLICATION_JSON;
    private String clientAcceptContentType;
    private String charsetName;
    // 1.4 data
    private byte[] data;
    private String txt = "";
    private File file;
    private boolean downloadMode = true;
    private String redirect;
    private final List<POI> poi = new ArrayList<>();
    private List<Memo> memo;
    private Map<String, Object> attributes;

    // 2.1 error
//    private int errorCode;
//    private String errorTag;
//    private Throwable cause;
    private ServiceError serviceError;
    private Throwable cause;
    // 2.2 logging control
    private Level level = Level.INFO;
    private boolean privacyReqHeader = false;
    private boolean privacyRespHeader = false;
    private boolean privacyReqContent = false;
    private boolean privacyRespContent = false;

    public static ServiceContext build(long hit) {
        return new ServiceContext(null, hit, System.currentTimeMillis(), null, null, null, null);
    }

    public static ServiceContext build(ChannelHandlerContext ctx, long hit, long startTs, HttpHeaders requestHeaders, HttpMethod requesMethod, String requesURI, String requestBody) {
        return new ServiceContext(ctx, hit, startTs, requestHeaders, requesMethod, requesURI, requestBody);
    }

    @Override
    public String toString() {
        //return "ServiceContext{" + "status=" + status + ", responseHeader=" + responseHeader + ", contentType=" + contentType + ", data=" + data + ", txt=" + txt + ", errorCode=" + errorCode + ", errorTag=" + errorTag + ", cause=" + cause + ", level=" + level + ", logReqHeader=" + privacyReqHeader + ", logRespHeader=" + privacyRespHeader + ", logReqContent=" + privacyReqContent + ", logRespContent=" + privacyRespContent + '}';
        return "ServiceContext{" + "status=" + status + ", responseHeaders=" + responseHeaders + ", contentType=" + contentType + ", data=" + data + ", txt=" + txt + ", errors=" + serviceError + ", level=" + level + ", logReqHeader=" + privacyReqHeader + ", logRespHeader=" + privacyRespHeader + ", logReqContent=" + privacyReqContent + ", logRespContent=" + privacyRespContent + '}';
    }

    private ServiceContext(ChannelHandlerContext ctx, long hit, long startTs, HttpHeaders requestHeaders, HttpMethod requesMethod, String requesURI, String requestBody) {
        if (ctx != null && ctx.channel() != null) {
            this.localIP = ctx.channel().localAddress();
            this.remoteIP = ctx.channel().remoteAddress();
        } else {
            this.localIP = null;
            this.remoteIP = null;
        }
        this.hit = hit;
        this.startTs = startTs;
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
     * get attribute value by kay
     *
     * @param key
     * @return
     */
    public Object attribute(String key) {
        return attributes == null ? null : attributes.get(key);
    }

    /**
     * set or remove attribute value
     *
     * @param key
     * @param value remove key-value if value is null, otherwise add key-value
     * @return current ServiceContext instance
     */
    public ServiceContext attribute(String key, Object value) {
        if (attributes == null) {
            if (key == null || value == null) {
                return this;
            } else {
                attributes = new HashMap();
            }
        }
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
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

    public ServiceContext reset() {
        status = HttpResponseStatus.OK;
        autoConvertBlank200To204 = true;
        // 1.4 data
        data = null;
        txt = "";
        file = null;
        redirect = null;

        // 2.1 error
//        errorCode = 0;
//        errorTag = null;
//        cause = null;
        serviceError = null;
        cause = null;
        // 2.2 logging control
        level = Level.INFO;
        privacyReqHeader = false;
        privacyRespHeader = false;
        privacyReqContent = false;
        privacyRespContent = false;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
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
            this.responseHeaders = new DefaultHttpHeaders(true);
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
            responseHeaders = new DefaultHttpHeaders(true);
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
            responseHeaders = new DefaultHttpHeaders(true);
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
            responseHeaders = new DefaultHttpHeaders(true);
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
        String realPath = folder.getAbsoluteFile().toPath().normalize().toString();
        memo("folder.view", filePath);

        if (!folder.exists()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_FOUND, null, "⚠", null);
            this.status(HttpResponseStatus.NOT_FOUND).error(e);
            return false;
        }

        if (!NioHttpUtil.sanitizePath(filePath) || !filePath.equals(realPath)
                || !folder.isDirectory() || folder.isFile()
                || folder.isHidden() || !folder.canRead()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_ACCESSABLE, null, "⚠", null);
            // 2. build JSON response with same app error code, and keep the default INFO log level.
            this.status(HttpResponseStatus.FORBIDDEN).error(e);
            return false;
        }
        return true;
    }

    public boolean precheckFile(File file, boolean isDownloadMode) {
        this.file = null;
        String filePath = file.getAbsolutePath();
        String realPath = file.getAbsoluteFile().toPath().normalize().toString();
        memo("file." + (isDownloadMode ? "download" : "view"), filePath);

        if (!file.exists()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_FOUND, null, "⚠", null);
            this.status(HttpResponseStatus.NOT_FOUND).error(e);
            return false;
        }

        if (!NioHttpUtil.sanitizePath(filePath) || !filePath.equals(realPath)
                || file.isDirectory() || !file.isFile()
                || file.isHidden() || !file.canRead()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Err e = new Err(BootErrorCode.FILE_NOT_ACCESSABLE, null, "⚠", null);
            // 2. build JSON response with same app error code, and keep the default INFO log level.
            this.status(HttpResponseStatus.FORBIDDEN).error(e);
            return false;
        }
        return true;
    }

//    private static final List<Integer> ERRPR_PAGES = new ArrayList();
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
    public ServiceContext file(File file, boolean isDownloadMode) {
        this.downloadMode = isDownloadMode;
        return this.file(file);
    }

    public ServiceContext file(File file) {
        if (!precheckFile(file, downloadMode)) {
            String errorFileName = status.code() + (downloadMode ? ".txt" : ".html");
            file = new File(nioCfg.getDocrootDir() + File.separator + nioCfg.getWebResources()
                    + File.separator + errorFileName).getAbsoluteFile();
        }
        this.txt = null;
        this.redirect = null;
        this.file = file;
        this.contentType = NioHttpUtil.getFileContentType(file);
        if (!downloadMode) {
            serviceError = null;
        }

        if (responseHeaders == null) {
            responseHeaders = new DefaultHttpHeaders(true);
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
        this.status(HttpResponseStatus.OK);
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
    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public ServiceError error() {
        if (serviceError == null || serviceError.getErrors() == null || serviceError.getErrors().isEmpty()) {
            return null;
        }
        return serviceError;
    }

    public ServiceContext error(Err error) {
        if (error == null) {
            return this;
        }
        if (serviceError == null) {
            serviceError = new ServiceError(hit);
        }
        serviceError.addError(error);
        Throwable t = error.getEx();
        if (t != null) {
            cause = t;
        }
        // set log level
        if (error.getEx() != null) {
            level = Level.ERROR;
        }
        return this;
    }

    public ServiceContext errors(Collection<Err> es) {
        if (es == null || es.isEmpty()) {
            if (serviceError != null && serviceError.getErrors() != null) {
                serviceError.getErrors().clear();
                serviceError = null;
            }
            return this;
        }
        if (serviceError == null) {
            serviceError = new ServiceError(hit);
        }
        serviceError.addErrors(es);
        for (Err e : es) {
            Throwable t = e.getEx();
            if (t != null) {
                cause = t;
            }
            if (cause != null) {
                level = Level.ERROR;
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
                if (level.isLessSpecificThan(Level.WARN)) {
                    level = Level.WARN;
                }
            } else {
                if (level.isLessSpecificThan(Level.ERROR)) {
                    level = Level.ERROR;
                }
            }
        }
        return this;
    }

    public Throwable cause() {
        return cause;
    }

    // 2.2 logging control
    public Level level() {
        return level;
    }

    public ServiceContext level(Level level) {
        this.level = level;
        return this;
    }

    public ServiceContext privacyReqHeader(boolean enabled) {
        this.privacyReqHeader = enabled;
        return this;
    }

    public boolean privacyReqHeader() {
        return privacyReqHeader;
    }

    public ServiceContext privacyReqContent(boolean enabled) {
        this.privacyReqContent = enabled;
        return this;
    }

    public boolean privacyReqContent() {
        return privacyReqContent;
    }

    public ServiceContext privacyRespHeader(boolean enabled) {
        this.privacyRespHeader = enabled;
        return this;
    }

    public boolean privacyRespHeader() {
        return privacyRespHeader;
    }

    public ServiceContext privacyRespContent(boolean enabled) {
        this.privacyRespContent = enabled;
        return this;
    }

    public boolean privacyRespContent() {
        return privacyRespContent;
    }

    public ServiceContext timestampPOI(String marker) {
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

    public ServiceContext reportMemo(StringBuilder sb) {
        if (memo == null || memo.isEmpty()) {
            sb.append("\n\tMemo: n/a");
            return this;
        }
        sb.append("\n\n\tMemo: ");
        memo.forEach((m) -> {
            sb.append("\n\t\t").append(m.id).append("=").append(m.desc);
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
        sb.append("\n\tPOI: ");
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
