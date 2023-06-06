package org.server.protocol.http.entity;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class StartLine {
    private String method;
    private String requestTarget;
    private String httpVersion;

    public StartLine() {
    }

    public StartLine(String method, String requestTarget, String httpVersion) {
        this.method = method;
        this.requestTarget = requestTarget;
        this.httpVersion = httpVersion;
    }


    public static StartLine parse(String readLine) {
        String[] readLineArr = readLine.split(" ");
        String requestTarget;
        try {
            requestTarget = URLDecoder.decode(readLineArr[1], "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return new StartLine(readLineArr[0], requestTarget, readLineArr[2]);
    }

    public String getMethod() {
        return method;
    }

    public StartLine method(String method) {
        this.method = method;
        return self();
    }

    public String getRequestTarget() {
        return requestTarget;
    }

    public StartLine requestTarget(String requestTarget) {
        this.requestTarget = requestTarget;
        return self();
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public StartLine httpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
        return self();
    }

    @Override
    public String toString() {
        return "StartLine{" +
                "method='" + method + '\'' +
                ", requestTarget='" + requestTarget + '\'' +
                ", httpVersion='" + httpVersion + '\'' +
                '}';
    }

    public static StartLine builder() {
        return new StartLine();
    }

    private StartLine self() {
        return this;
    }


}
