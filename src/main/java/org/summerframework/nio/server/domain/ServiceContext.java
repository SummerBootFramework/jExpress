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
package org.summerframework.nio.server.domain;

import org.summerframework.nio.server.HttpConfig;
import org.summerframework.nio.server.NioHttpUtil;
import org.summerframework.security.auth.Caller;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.summerframework.boot.BootErrorCode;
import org.summerframework.boot.BootPOI;
import org.summerframework.nio.server.NioConfig;
import java.util.Set;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author Changski Tie Zheng Zhang, Du Xiao
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ServiceContext {

    //private ChannelHandlerContext ctx;
    private final SocketAddress localIP;
    private final SocketAddress remoteIP;
    private final long hit;
    private final long startTs;
    private Caller caller;
    private String callerId;

    //  1.1 status
    private HttpResponseStatus status = HttpResponseStatus.FORBIDDEN;
    // 1.2 headers
    private HttpHeaders headers;
    // 1.3 content type    
    private String contentType = MediaType.APPLICATION_JSON;
    private String charsetName;
    // 1.4 data
    private byte[] data;
    private String txt = "";
    private File file;
    private boolean downloadMode;
    private String redirect;
    private final List<POI> poi = new ArrayList<>();
    private List<Memo> memo;

    // 2.1 error
//    private int errorCode;
//    private String errorTag;
//    private Throwable cause;
    private ServiceError errors;
    private Throwable cause;
    // 2.2 logging control
    private Level level = Level.INFO;
    private boolean privacyReqHeader = false;
    private boolean privacyRespHeader = false;
    private boolean privacyReqContent = false;
    private boolean privacyRespContent = false;

    public static ServiceContext build(long hit) {
        return new ServiceContext(null, hit, System.currentTimeMillis());
    }

    public static ServiceContext build(ChannelHandlerContext ctx, long hit, long startTs) {
        return new ServiceContext(ctx, hit, startTs);
    }

    @Override
    public String toString() {
        //return "ServiceContext{" + "status=" + status + ", headers=" + headers + ", contentType=" + contentType + ", data=" + data + ", txt=" + txt + ", errorCode=" + errorCode + ", errorTag=" + errorTag + ", cause=" + cause + ", level=" + level + ", logReqHeader=" + privacyReqHeader + ", logRespHeader=" + privacyRespHeader + ", logReqContent=" + privacyReqContent + ", logRespContent=" + privacyRespContent + '}';
        return "ServiceContext{" + "status=" + status + ", headers=" + headers + ", contentType=" + contentType + ", data=" + data + ", txt=" + txt + ", errors=" + errors + ", level=" + level + ", logReqHeader=" + privacyReqHeader + ", logRespHeader=" + privacyRespHeader + ", logReqContent=" + privacyReqContent + ", logRespContent=" + privacyRespContent + '}';
    }

    private ServiceContext(ChannelHandlerContext ctx, long hit, long startTs) {
        if (ctx != null && ctx.channel() != null) {
            this.localIP = ctx.channel().localAddress();
            this.remoteIP = ctx.channel().remoteAddress();
        } else {
            this.localIP = null;
            this.remoteIP = null;
        }
        this.hit = hit;
        this.startTs = startTs;
        poi.add(new POI(BootPOI.SERVICE_BEGIN));
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
        status = HttpResponseStatus.FORBIDDEN;
        // 1.4 data
        data = null;
        txt = "";
        file = null;
        redirect = null;

        // 2.1 error
//        errorCode = 0;
//        errorTag = null;
//        cause = null;
        errors = null;
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

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public HttpResponseStatus status() {
        return status;
    }

    public ServiceContext status(HttpResponseStatus status) {
        this.status = status;
        return this;
    }

    //@JsonInclude(JsonInclude.Include.NON_NULL)
    public HttpHeaders headers() {
        return headers;
    }

    public ServiceContext headers(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return this;
        }
        if (this.headers == null) {
            this.headers = new DefaultHttpHeaders(true);
        }
        this.headers.set(headers);
        return this;
    }

//        public Response addHeader(String key, Object value) {
//            if (StringUtils.isBlank(key) || value == null) {
//                return this;
//            }
//            if (headers == null) {
//                headers = new DefaultHttpHeaders(true);
//            }
//            headers.add(key, value);
//            return this;
//        }
    public ServiceContext header(String key, Object value) {
        if (StringUtils.isBlank(key)) {
            return this;
        }
        if (headers == null) {
            headers = new DefaultHttpHeaders(true);
        }
        if (value == null) {
            headers.remove(key);
        } else {
            headers.set(key, value);
        }
        return this;
    }

//        public Response addHeaders(String key, Iterable<?> values) {
//            if (StringUtils.isBlank(key) || values == null) {
//                return this;
//            }
//            if (headers == null) {
//                headers = new DefaultHttpHeaders(true);
//            }
//            headers.add(key, values);
//            return this;
//        }
    public ServiceContext header(String key, Iterable<?> values) {
        if (StringUtils.isBlank(key)) {
            return this;
        }
        if (headers == null) {
            headers = new DefaultHttpHeaders(true);
        }
        if (values == null) {
            headers.remove(key);
        } else {
            headers.set(key, values);
        }
        return this;
    }

    public ServiceContext headers(Map<String, Iterable<?>> hs) {
        if (hs == null) {
            return this;
        }
        if (headers == null) {
            headers = new DefaultHttpHeaders(true);
        }
        hs.keySet().stream().filter((key) -> (StringUtils.isNotBlank(key))).forEachOrdered((key) -> {
            Iterable<?> values = hs.get(key);
            if (values == null) {
                headers.remove(key);
            } else {
                headers.set(key, values);
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

    public ServiceContext contentTypeTry(String contentType) {
        if (contentType != null) {
            this.contentType = contentType;
        }
        return this;
    }

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
        this.redirect = redirect;
        this.txt = null;
        this.file = null;
        this.status = HttpResponseStatus.TEMPORARY_REDIRECT;
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

    public boolean precheckFolder(File folder) {
        this.file = null;
        String filePath = folder.getAbsolutePath();
        String realPath = folder.getAbsoluteFile().toPath().normalize().toString();
        memo("folder.view", filePath);

        if (!folder.exists()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Error e = new Error(BootErrorCode.FILE_NOT_FOUND, null, "⚠", null);
            this.status(HttpResponseStatus.NOT_FOUND).error(e);
            return false;
        }

        if (!NioHttpUtil.sanitizePath(filePath) || !filePath.equals(realPath)
                || !folder.isDirectory() || folder.isFile()
                || folder.isHidden() || !folder.canRead()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Error e = new Error(BootErrorCode.FILE_NOT_ACCESSABLE, null, "⚠", null);
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
            Error e = new Error(BootErrorCode.FILE_NOT_FOUND, null, "⚠", null);
            this.status(HttpResponseStatus.NOT_FOUND).error(e);
            return false;
        }

        if (!NioHttpUtil.sanitizePath(filePath) || !filePath.equals(realPath)
                || file.isDirectory() || !file.isFile()
                || file.isHidden() || !file.canRead()) {
            //var e = new ServiceError(appErrorCode, null, "⚠", null);
            Error e = new Error(BootErrorCode.FILE_NOT_ACCESSABLE, null, "⚠", null);
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
//            File errorFile = new File(HttpConfig.CFG.getDocroot() + File.separator + HttpConfig.CFG.getWebResources()
//                     + File.separator + errorFileName).getAbsoluteFile();
//            file(errorFile, false);
//        }
//        return this;
//    }
    public ServiceContext file(File file, boolean isDownloadMode) {
        if (!precheckFile(file, isDownloadMode)) {
            String errorFileName = status.code() + (isDownloadMode ? ".txt" : ".html");
            file = new File(HttpConfig.CFG.getDocroot() + File.separator + HttpConfig.CFG.getWebResources()
                    + File.separator + errorFileName).getAbsoluteFile();
        }
        this.txt = null;
        this.redirect = null;
        this.file = file;
        this.downloadMode = isDownloadMode;
        this.contentType = NioHttpUtil.getFileContentType(file);
        if (!isDownloadMode) {
            errors = null;
        }

        if (headers == null) {
            headers = new DefaultHttpHeaders(true);
        }
        long fileLength = file.length();
        if (fileLength > Integer.MAX_VALUE) {
            headers.set(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(fileLength));
        } else {
            headers.setInt(HttpHeaderNames.CONTENT_LENGTH, (int) fileLength);
        }
        headers.set(HttpHeaderNames.CONTENT_TYPE, contentType);
        if (isDownloadMode) {
            String fileName = file.getName();
            try {
                fileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            } catch (UnsupportedEncodingException ex) {
            }
            headers.set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment;filename=" + fileName + ";filename*=UTF-8''" + fileName);
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
        if (errors == null || errors.getErrors() == null || errors.getErrors().isEmpty()) {
            return null;
        }
        return errors;
    }

    public ServiceContext error(Error error) {
        if (error == null) {
            return this;
        }
        if (errors == null) {
            errors = new ServiceError(hit);
        }
        errors.addError(error);
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

    public ServiceContext errors(Collection<Error> es) {
        if (es == null || es.isEmpty()) {
            return this;
        }
        if (errors == null) {
            errors = new ServiceError(hit);
        }
        errors.addErrors(es);
        for (Error e : es) {
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

    public void reportMemo(StringBuilder sb) {
        if (memo == null || memo.isEmpty()) {
            sb.append("\n\tMemo: n/a");
            return;
        }
        sb.append("\n\n\tMemo: ");
        memo.forEach((m) -> {
            sb.append("\n\t\t").append(m.id).append("=").append(m.desc);
        });
    }

    public void reportPOI(NioConfig cfg, StringBuilder sb) {
        if (poi == null || poi.isEmpty()) {
            sb.append("\n\tPOI: n/a");
            return;
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
    }
}
