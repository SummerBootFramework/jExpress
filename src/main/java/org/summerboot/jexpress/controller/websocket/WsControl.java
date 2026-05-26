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

package org.summerboot.jexpress.controller.websocket;

import io.netty.handler.codec.stomp.StompCommand;

import java.util.HashMap;
import java.util.Map;

public class WsControl {

    enum Status {
        CONNECT, CONNECTED, DISCONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, MESSAGE, FILE,
        ERROR,
        UPLOAD_CLIENT_START, UPLOAD_SERVER_RECEIVED_CHUNK, UPLOAD_SERVER_RECEIVED_FULL, UPLOAD_SERVER_AUDIT_COMPLETE, UPLOAD_SERVER_AUDIT_FAILED, UPLOAD_CLIENT_COMPLETE
    }

    private Status status;
    private String msg;
    private Long num;

    private String mimeType;
    private String fileType;
    private String fileExtension;
    private String fileName;


    private StompCommand stomp;
    private Map<String, String> headers;
    private String body;

    public WsControl() {
    }

    public WsControl(StompCommand stomp) {
        this.stomp = stomp;
    }

    public WsControl(StompCommand stomp, String body) {
        this.stomp = stomp;
        this.body = body;
    }


    public Map<String, String> headers() {
        return headers == null ? new HashMap<>() : headers;
    }

    public WsControl(Status status) {
        this.status = status;
    }

    public WsControl(Status status, String msg) {
        this.status = status;
        this.msg = msg;
    }

    public WsControl(Status status, Long num) {
        this.status = status;
        this.num = num;
    }


    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Long getNum() {
        return num;
    }

    public void setNum(Long num) {
        this.num = num;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
