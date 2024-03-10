package com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

import java.io.File;
import java.util.List;

/**
 * 下载返回结果
 * 当下载成功时，file字段和cachedFileKey字段二者只会有一个不为null，作为文件下载结果
 *
 * @author pf-miles
 * <p>
 * 2022-12-10 00:20
 */
public class AsyncHttpClientDownloadResult {
    // param中debug=true 时才会有
    private List<String> requestHeaders;
    // param中debug=true 时才会有
    private List<String> responseHeaders;
    // 下载好的文件, 当该字段为null时，可能是下载失败；也可能是在启用头部数据去重时命中了去重逻辑，此时用户应取'cachedFileKey'字段值作为结果
    private File file;
    // 当启用头部数据去重时，去重逻辑成功命中后返回的缓存文件位置字符串表示, 该表示由用户自行定义
    private String cachedFileKey;
    // response中返回的文件名
    private String remoteFileName;
    // 错误详情
    private String errMsg;
    // 错误码，0表示成功，其它均表示异常
    private int errCode;
    // 下载文件的请求的response，仅当未命中头部数据去重逻辑时, 其携带的entity才有效
    private HttpResponse httpResponse;

    // 下载response的entity类型, 可能为null(未知)
    private ContentType contentType;
    // 下载数据entity的encoding，可能为null(未知)
    private Header contentEncoding;
    // 下载到的文件的大小, 可能为0(未知)
    private long fileSize;

    public List<String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(List<String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public List<String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(List<String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getRemoteFileName() {
        return remoteFileName;
    }

    public void setRemoteFileName(String remoteFileName) {
        this.remoteFileName = remoteFileName;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public int getErrCode() {
        return errCode;
    }

    public void setErrCode(int errCode) {
        this.errCode = errCode;
    }

    public String getCachedFileKey() {
        return cachedFileKey;
    }

    public void setCachedFileKey(String cachedFileKey) {
        this.cachedFileKey = cachedFileKey;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public Header getContentEncoding() {
        return contentEncoding;
    }

    public void setContentEncoding(Header contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }
}
