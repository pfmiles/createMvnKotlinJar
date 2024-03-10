package com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient;

/**
 * @author pf-miles
 * <p>
 * 2022-12-10 21:44
 */
public class DownloadSizeExceedsLimitException extends RuntimeException {
    public DownloadSizeExceedsLimitException() {
        super();
    }

    public DownloadSizeExceedsLimitException(String message) {
        super(message);
    }

    public DownloadSizeExceedsLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
