package com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient;

/**
 * @author pf-miles
 * <p>
 * 2022-12-10 17:16
 */
public class HttpNot200Exception extends RuntimeException {
    public HttpNot200Exception() {
        super();
    }

    public HttpNot200Exception(String message) {
        super(message);
    }

    public HttpNot200Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public HttpNot200Exception(Throwable cause) {
        super(cause);
    }
}
