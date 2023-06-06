package org.server.protocol.http.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.server.entity.CompositeByteBuf;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class RequestHeaders {
    protected static final Logger LOGGER = LogManager.getLogger(RequestHeaders.class);
    private String accept;
    private String acceptEncoding;
    private String acceptLanguage;
    private String connection;
    private Integer contentLength;
    private String contentType;
    private String host;
    private String date;
    private String contentLanguage;
    private String cacheControl;
    private String userAgent;
    private String secchua;
    private String secchuamobile;
    private String secchuaplatform;
    private String secFetchSite;
    private String secFetchMode;
    private String secFetchDest;
    private String secFetchUser;
    private String upgradeInsecureRequests;
    private String referer;
    private String proxyConnection;
    private String proxyAuthorization;
    /**
     * 如下websocket支持
     */
    private String upgrade;
    private String secWebSocketKey;
    private Integer secWebSocketVersion;
    //包含了一个或者多个客户端希望使用的用逗号分隔的根据权重排序的子协议。
    private String secWebSocketProtocol;
    private String origin;
    //表示客户端期望使用的协议级别的扩展
    private String secWebSocketExtensions;

    public static RequestHeaders builder() {
        return new RequestHeaders();
    }

    private RequestHeaders self() {
        return this;
    }

    public static RequestHeaders parse(CompositeByteBuf cumulation) {
        RequestHeaders requestHeaders = new RequestHeaders();
        while (cumulation.remaining() > 0) {
            String requestLine = cumulation.readLine();
            //说明读取结束，则关闭流，连续读取到2个\r\n则退出
            if (requestLine.length() == 0) {
                break;
            }
            String[] arr = requestLine.split(":");
            try {
                String key = arr[0];
                Object value = arr[1].trim();
                int pre = key.indexOf("-");
                if (pre > 0) {
                    String substring = key.substring(0, pre).toLowerCase();
                    String s = key.substring(pre, key.length()).replaceAll("-", "");
                    key = substring + s;
                } else {
                    key = key.toLowerCase();
                }
                Field field = requestHeaders.getClass().getDeclaredField(key);
                if (field.getType() != String.class) {
                    Class<?> type = field.getType();
                    value = type.getConstructor(String.class).newInstance(value);
                }
                field.setAccessible(true);
                field.set(requestHeaders, value);
            } catch (NoSuchFieldException e) {
                LOGGER.error("NoSuchFieldException {}: {}", arr[0], arr[1]);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return requestHeaders;
    }

    public String getAccept() {
        return accept;
    }

    public RequestHeaders accept(String accept) {
        this.accept = accept;
        return self();
    }

    public String getAcceptEncoding() {
        return acceptEncoding;
    }

    public RequestHeaders acceptEncoding(String acceptEncoding) {
        this.acceptEncoding = acceptEncoding;
        return self();
    }

    public String getAcceptLanguage() {
        return acceptLanguage;
    }

    public RequestHeaders acceptLanguage(String acceptLanguage) {
        this.acceptLanguage = acceptLanguage;
        return self();
    }

    public String getConnection() {
        return connection;
    }

    public RequestHeaders connection(String connection) {
        this.connection = connection;
        return self();
    }

    public String getHost() {
        return host;
    }

    public RequestHeaders host(String host) {
        this.host = host;
        return self();
    }

    public String getCacheControl() {
        return cacheControl;
    }

    public RequestHeaders cacheControl(String cacheControl) {
        this.cacheControl = cacheControl;
        return self();
    }

    public String getUserAgent() {
        return userAgent;
    }

    public RequestHeaders userAgent(String userAgent) {
        this.userAgent = userAgent;
        return self();
    }

    public Integer getContentLength() {
        return contentLength;
    }

    public RequestHeaders contentLength(Integer contentLength) {
        this.contentLength = contentLength;
        return self();
    }

    public String getContentType() {
        return contentType;
    }

    public RequestHeaders contentType(String contentType) {
        this.contentType = contentType;
        return self();
    }

    public String getUpgrade() {
        return upgrade;
    }

    public RequestHeaders upgrade(String upgrade) {
        this.upgrade = upgrade;
        return self();
    }

    public String getSecWebSocketKey() {
        return secWebSocketKey;
    }

    public RequestHeaders secWebSocketKey(String secWebSocketKey) {
        this.secWebSocketKey = secWebSocketKey;
        return self();
    }

    public Integer getSecWebSocketVersion() {
        return secWebSocketVersion;
    }

    public RequestHeaders secWebSocketVersion(Integer secWebSocketVersion) {
        this.secWebSocketVersion = secWebSocketVersion;
        return self();
    }

    public String getSecWebSocketProtocol() {
        return secWebSocketProtocol;
    }

    public RequestHeaders secWebSocketProtocol(String secWebSocketProtocol) {
        this.secWebSocketProtocol = secWebSocketProtocol;
        return self();
    }

    public String getOrigin() {
        return origin;
    }

    public RequestHeaders origin(String origin) {
        this.origin = origin;
        return self();
    }

    public String getSecWebSocketExtensions() {
        return secWebSocketExtensions;
    }

    public RequestHeaders secWebSocketExtensions(String secWebSocketExtensions) {
        this.secWebSocketExtensions = secWebSocketExtensions;
        return self();
    }

    public String getSecchua() {
        return secchua;
    }

    public RequestHeaders secchua(String secchua) {
        this.secchua = secchua;
        return self();
    }

    public String getSecchuamobile() {
        return secchuamobile;
    }

    public RequestHeaders secchuamobile(String secchuamobile) {
        this.secchuamobile = secchuamobile;
        return self();
    }

    public String getSecchuaplatform() {
        return secchuaplatform;
    }

    public RequestHeaders secchuaplatform(String secchuaplatform) {
        this.secchuaplatform = secchuaplatform;
        return self();
    }

    public String getsecFetchSite() {
        return secFetchSite;
    }

    public RequestHeaders secFetchSite(String secFetchSite) {
        this.secFetchSite = secFetchSite;
        return self();
    }

    public String getSecFetchMode() {
        return secFetchMode;
    }

    public RequestHeaders secFetchMode(String secFetchMode) {
        this.secFetchMode = secFetchMode;
        return self();
    }

    public String getSecFetchDest() {
        return secFetchDest;
    }

    public RequestHeaders secFetchDest(String secFetchDest) {
        this.secFetchDest = secFetchDest;
        return self();
    }

    public String getReferer() {
        return referer;
    }

    public RequestHeaders referer(String referer) {
        this.referer = referer;
        return self();
    }

    public String getSecFetchUser() {
        return secFetchUser;
    }

    public RequestHeaders secFetchUser(String secFetchUser) {
        this.secFetchUser = secFetchUser;
        return self();
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setContentLanguage(String contentLanguage) {
        this.contentLanguage = contentLanguage;
    }

    public String getUpgradeInsecureRequests() {
        return upgradeInsecureRequests;
    }

    public RequestHeaders upgradeInsecureRequests(String upgradeInsecureRequests) {
        this.upgradeInsecureRequests = upgradeInsecureRequests;
        return self();
    }

    public String getProxyConnection() {
        return proxyConnection;
    }

    public RequestHeaders proxyConnection(String proxyConnection) {
        this.proxyConnection = proxyConnection;
        return self();
    }

    public String getProxyAuthorization() {
        return proxyAuthorization;
    }

    public RequestHeaders proxyAuthorization(String proxyAuthorization) {
        this.proxyAuthorization = proxyAuthorization;
        return self();
    }

    @Override
    public String toString() {
        return "RequestHeaders{" +
                "accept='" + accept + '\'' +
                ", acceptEncoding='" + acceptEncoding + '\'' +
                ", acceptLanguage='" + acceptLanguage + '\'' +
                ", connection='" + connection + '\'' +
                ", contentLength=" + contentLength +
                ", contentType='" + contentType + '\'' +
                ", host='" + host + '\'' +
                ", date='" + date + '\'' +
                ", contentLanguage='" + contentLanguage + '\'' +
                ", cacheControl='" + cacheControl + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", secchua='" + secchua + '\'' +
                ", secchuamobile='" + secchuamobile + '\'' +
                ", secchuaplatform='" + secchuaplatform + '\'' +
                ", secFetchSite='" + secFetchSite + '\'' +
                ", secFetchMode='" + secFetchMode + '\'' +
                ", secFetchDest='" + secFetchDest + '\'' +
                ", secFetchUser='" + secFetchUser + '\'' +
                ", upgradeInsecureRequests='" + upgradeInsecureRequests + '\'' +
                ", referer='" + referer + '\'' +
                ", proxyConnection='" + proxyConnection + '\'' +
                ", proxyAuthorization='" + proxyAuthorization + '\'' +
                ", upgrade='" + upgrade + '\'' +
                ", secWebSocketKey='" + secWebSocketKey + '\'' +
                ", secWebSocketVersion=" + secWebSocketVersion +
                ", secWebSocketProtocol='" + secWebSocketProtocol + '\'' +
                ", origin='" + origin + '\'' +
                ", secWebSocketExtensions='" + secWebSocketExtensions + '\'' +
                '}';
    }
}
