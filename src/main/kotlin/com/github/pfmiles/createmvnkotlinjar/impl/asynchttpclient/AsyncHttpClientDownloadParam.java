package com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient;

import kotlin.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.util.List;

/**
 * @author pf-miles
 * <p>
 * 2022-12-10 00:20
 */
public class AsyncHttpClientDownloadParam {
    // 下载地址, 必填
    private String url;
    // 指定自定义下载目标文件，必填
    private File targetFile;
    // 将要使用的代理字符串，形如：[protocol://][user:password@]proxyhost[:port]
    private String proxy;
    // 指定不走代理的hosts
    private List<String> noProxyHosts;
    // 下载时使用的usage agent
    private String userAgent;
    // 指定请求中要发送的cookie
    private String cookies;
    // 指定连接超时时间, s
    private int connectTimeout = 30;
    // 整个下载过程总耗时限制, s
    private int totalTimeout = 300;
    // 指定referer
    private String referer;
    // 要设置的自定义header列表，若请求中原本已有该header，则覆盖，若想设置无值header，则将pair的right置为null
    private List<Pair<String, String>> headers;
    // 要删除的header列表
    private List<String> deleteHeaders;
    // 强制校验服务端证书，默认关闭，即服务端证书非法也可下载
    private boolean validateCaCert;
    // 是否跟随跳转
    private boolean followRedirects = true;
    // 跟随跳转的最大次数
    private int maxRedirect = 5;
    // 以KB计的下载速度限制
    private int maxDownloadSpeed;
    // 文件大小限制(bytes)，超过此限制将不会下载
    private long maxFileSize;
    // 最大重试次数，当发生4xx/5xx或超时错误时将重试，默认不重试
    private int maxRetry;
    // 指定hosts绑定
    private List<Triple<String, Integer, String>> hostsBindings;
    // 是否开启"低速下载"限制机制, 开启后，若下载进程连续在lowSpeedTimeLimit以上时长下载速度低于lowSpeedWaterline，则放弃下载，报错退出
    private boolean lowSpeedLimitOn;
    // 定义为"低速"下载的速度限制(bytes/s)，实时下载速度若低于这个值，则将被认为是"低速下载"，默认1KB/s
    private long lowSpeedWaterline = 1024L;
    // 陷入"低速下载"情况的最大时长(s)，超过这个时间将放弃下载，报错退出
    private int lowSpeedTimeLimit = 30;
    // 是否返回req/resp headers信息，默认false
    private boolean debug;
    // 是否强制以http 1.0发送请求，默认http 1.1
    private boolean forceHttp1;
    // 是否开启头部数据去重下载功能
    private boolean headDataDedupOn;
    // 定义"头部数据"的长度, bytes
    private int headDataLength = 256 * 1024;
    // 开启头部数据去重下载功能后，将要使用的去重逻辑：接受头部数据，返回已经下载过的存储项的url(或唯一标识，按业务需要而定)，找不到则返回null
    private HeadDataDeduplicater headDedupLogic;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public File getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(File targetFile) {
        this.targetFile = targetFile;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public List<String> getNoProxyHosts() {
        return noProxyHosts;
    }

    public void setNoProxyHosts(List<String> noProxyHosts) {
        this.noProxyHosts = noProxyHosts;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getCookies() {
        return cookies;
    }

    public void setCookies(String cookies) {
        this.cookies = cookies;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getTotalTimeout() {
        return totalTimeout;
    }

    public void setTotalTimeout(int totalTimeout) {
        this.totalTimeout = totalTimeout;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public List<Pair<String, String>> getHeaders() {
        return headers;
    }

    public void setHeaders(List<Pair<String, String>> headers) {
        this.headers = headers;
    }

    public List<String> getDeleteHeaders() {
        return deleteHeaders;
    }

    public void setDeleteHeaders(List<String> deleteHeaders) {
        this.deleteHeaders = deleteHeaders;
    }

    public boolean isValidateCaCert() {
        return validateCaCert;
    }

    public void setValidateCaCert(boolean validateCaCert) {
        this.validateCaCert = validateCaCert;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public int getMaxRedirect() {
        return maxRedirect;
    }

    public void setMaxRedirect(int maxRedirect) {
        this.maxRedirect = maxRedirect;
    }

    public int getMaxDownloadSpeed() {
        return maxDownloadSpeed;
    }

    public void setMaxDownloadSpeed(int maxDownloadSpeed) {
        this.maxDownloadSpeed = maxDownloadSpeed;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public List<Triple<String, Integer, String>> getHostsBindings() {
        return hostsBindings;
    }

    public void setHostsBindings(List<Triple<String, Integer, String>> hostsBindings) {
        this.hostsBindings = hostsBindings;
    }

    public boolean isLowSpeedLimitOn() {
        return lowSpeedLimitOn;
    }

    public void setLowSpeedLimitOn(boolean lowSpeedLimitOn) {
        this.lowSpeedLimitOn = lowSpeedLimitOn;
    }

    public long getLowSpeedWaterline() {
        return lowSpeedWaterline;
    }

    public void setLowSpeedWaterline(long lowSpeedWaterline) {
        this.lowSpeedWaterline = lowSpeedWaterline;
    }

    public int getLowSpeedTimeLimit() {
        return lowSpeedTimeLimit;
    }

    public void setLowSpeedTimeLimit(int lowSpeedTimeLimit) {
        this.lowSpeedTimeLimit = lowSpeedTimeLimit;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isForceHttp1() {
        return forceHttp1;
    }

    public void setForceHttp1(boolean forceHttp1) {
        this.forceHttp1 = forceHttp1;
    }

    public boolean isHeadDataDedupOn() {
        return headDataDedupOn;
    }

    public void setHeadDataDedupOn(boolean headDataDedupOn) {
        this.headDataDedupOn = headDataDedupOn;
    }

    public int getHeadDataLength() {
        return headDataLength;
    }

    public void setHeadDataLength(int headDataLength) {
        this.headDataLength = headDataLength;
    }

    public HeadDataDeduplicater getHeadDedupLogic() {
        return headDedupLogic;
    }

    public void setHeadDedupLogic(HeadDataDeduplicater headDedupLogic) {
        this.headDedupLogic = headDedupLogic;
    }
}
