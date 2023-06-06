package org.client.protocol.http.entity;

import java.util.Arrays;

public enum HttpStatus {
    OK(200, "OK"), NOT_FOUND(404, "NOT_FOUND"), UPGRADE(101, "Switching Protocols");
    private int statusCode;
    private String reasonPhrase;

    HttpStatus(int statusCode, String reasonPhrase) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public void setReasonPhrase(String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
    }

    public static HttpStatus match(int statusCode) {
        return Arrays.stream(HttpStatus.values()).filter(it->it.getStatusCode()==statusCode).findFirst().get();
    }
}
