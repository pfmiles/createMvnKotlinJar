package com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient;

/**
 * 在下载的response中未找到下载数据entity
 *
 * @author pf-miles
 */
public class NoDownloadEntityFoundException extends RuntimeException {
    @Override
    public synchronized Throwable fillInStackTrace() {
        return null;
    }
}
