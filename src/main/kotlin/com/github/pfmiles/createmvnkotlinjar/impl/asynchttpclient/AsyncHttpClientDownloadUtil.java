package com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient;

import com.github.pfmiles.createmvnkotlinjar.impl.ExceptionUtils;
import com.github.pfmiles.createmvnkotlinjar.impl.Runner;
import com.github.pfmiles.createmvnkotlinjar.impl.async.FuturesMultiplexer;
import com.google.common.base.Preconditions;
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.conn.NHttpClientConnectionManager;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用asyncHttpClient的下载工具
 *
 * @author pf-miles
 * <p>
 * 2022-12-10 00:19
 */
public class AsyncHttpClientDownloadUtil {
    private static final int CPU_NUM = Runtime.getRuntime().availableProcessors();

    private static final Logger logger = LoggerFactory
            .getLogger(AsyncHttpClientDownloadUtil.class);
    // 当headDedup成功后，关闭当前下载io后，框架所抛出的错误信息
    private static final String HEAD_DEDUP_IO_CLOSE_ERR_MSG = "Connection closed unexpectedly";

    private static CloseableHttpAsyncClient client;
    private static final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    private static final Set<AuthScope> addedToCreds = Collections
            .newSetFromMap(new ConcurrentHashMap<>(16, 0.75f, CPU_NUM * 2));

    private static final AtomicInteger seq = new AtomicInteger();
    private static final ExecutorService respReaderPool = Executors
            .newCachedThreadPool(r -> new Thread(r,
                    "AsyncHttpClientDownloadUtil-response-handle-thread-" + seq.getAndIncrement()));

    private static final FuturesMultiplexer multiplexer = new FuturesMultiplexer(
            100);

    /**
     * 下载
     *
     * @param param 下载参数
     * @return 下载任务CompletableFuture
     */
    public static CompletableFuture<AsyncHttpClientDownloadResult> download(AsyncHttpClientDownloadParam param) {
        Preconditions.checkArgument(param != null && StringUtils.isNotBlank(param.getUrl())
                && param.getTargetFile() != null);
        if (param.isHeadDataDedupOn()) {
            Preconditions.checkArgument(param.getHeadDedupLogic() != null,
                    "headDedupLogic must be set when headChecksumDedup is true.");
            Preconditions.checkArgument(param.getHeadDataLength() >= 16 * 1024,
                    "'headDataLength' must be greater than 16KB, nonsense otherwise.");
        }

        // 这里插入HeadDataDedupResponseConsumer来做head256 dedup
        HeadDataDedupResponseConsumer consumer = new HeadDataDedupResponseConsumer(param.getUrl(),
                param.getHeadDataLength(), param.getHeadDedupLogic(), param.getTargetFile(),
                param.getMaxFileSize(), param.isHeadDataDedupOn());
        return multiplexer
                .submitFuture(getClient(param).execute(HttpAsyncMethods.create(createRequest(param)),
                        consumer, new FutureCallback<AsyncHttpClientDownloadResult>() {
                            @Override
                            public void completed(AsyncHttpClientDownloadResult result) {
                                if (logger.isInfoEnabled()) {
                                    // 下载成功的定义：无错误码/错误详情，且result中的file或cachedFileKey不为null
                                    if (result.getErrCode() == 0 && result.getErrMsg() == null
                                            && (result.getFile() != null && result.getFile().exists()
                                            || result.getCachedFileKey() != null)) {
                                        if (result.getCachedFileKey() != null) {
                                            logger.info(
                                                    "Downloading for url: {} success, the result is hit by headDataDedup: {}",
                                                    param.getUrl(), result.getCachedFileKey());
                                        } else {
                                            logger.info("Downloading for url: {} success.", param.getUrl());
                                        }
                                    } else {
                                        logger.error(
                                                "Downloading for url: {} failed, errCode: {}, errMsg: {}",
                                                param.getUrl(), result.getErrCode(), result.getErrMsg());
                                    }
                                }
                            }

                            @Override
                            public void failed(Exception ex) {
                                // 当headDedup成功时，关闭io之后会抛ConnectionClosedException
                                if (ex instanceof ConnectionClosedException
                                        && HEAD_DEDUP_IO_CLOSE_ERR_MSG.equals(ex.getMessage())
                                        && consumer.isDedupOn() && consumer.isHeadDedupExed()
                                        && consumer.getDedupResult() != null) {
                                    logger.info(
                                            "Head data dedup for url: {} success, cached file key returned: {}",
                                            param.getUrl(), consumer.getDedupResult());
                                } else {
                                    logger.error(String.format("Downloading for url: %s throws exception.",
                                            param.getUrl()), ex);
                                }
                            }

                            @Override
                            public void cancelled() {
                                logger.warn("Downloading for url: {} is canceled.", param.getUrl());
                            }
                        }), new Date(System.currentTimeMillis() + param.getTotalTimeout() * 1000L))
                .handleAsync((result, ex) -> {
                    try {
                        Preconditions.checkState(!(result == null && ex == null),
                                "result and ex are both null, impossible!");
                        if (result == null)
                            result = new AsyncHttpClientDownloadResult();
                        if (ex != null) {
                            // 当headDedup成功时，关闭io之后， ex.getCause()是ConnectionClosedException("Connection closed unexpectedly")
                            if (ex.getCause() != null
                                    && ex.getCause() instanceof ConnectionClosedException
                                    && HEAD_DEDUP_IO_CLOSE_ERR_MSG.equals(ex.getCause().getMessage())
                                    && consumer.isDedupOn() && consumer.isHeadDedupExed()
                                    && consumer.getDedupResult() != null) {
                                result.setRemoteFileName(HeadDataDedupResponseConsumer
                                        .resolveFileNameFromURL(param.getUrl()));
                                result.setCachedFileKey(consumer.getDedupResult());
                                BasicStatusLine statusLine = new BasicStatusLine(
                                        new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK,
                                        "HeadDedup success.");
                                BasicHttpResponse dummyResponse = new BasicHttpResponse(statusLine);
                                result.setHttpResponse(dummyResponse);
                            } else {
                                // 下载过程有错误抛出，
                                Pair<Integer, String> codeNMsg = resolveErrCodeAndMsg(ex);
                                result.setErrCode(codeNMsg.getFirst());
                                result.setErrMsg(codeNMsg.getSecond());
                            }
                            return result;
                        } else {
                            return result;
                        }
                    } finally {
                        consumer.releaseResources();
                    }
                }, respReaderPool);
    }

    private static HttpUriRequest createRequest(AsyncHttpClientDownloadParam param) {
        RequestBuilder builder = RequestBuilder.get().setUri(param.getUrl())
                .setConfig(createReqConf(param));
        if (param.isForceHttp1())
            builder.setVersion(HttpVersion.HTTP_1_0);
        for (Pair<String, String> p : param.getHeaders()) {
            builder.addHeader(p.getFirst(), p.getSecond());
        }
        return builder.build();
    }

    private static RequestConfig createReqConf(AsyncHttpClientDownloadParam param) {
        return RequestConfig.custom().setConnectTimeout(param.getConnectTimeout() * 1000)
                .setConnectionRequestTimeout(param.getConnectTimeout() * 1000)
                .setRedirectsEnabled(param.isFollowRedirects())
                .setSocketTimeout((param.getTotalTimeout() - param.getConnectTimeout()) * 1000)
                .setCircularRedirectsAllowed(false).setCookieSpec(CookieSpecs.DEFAULT)
                .setExpectContinueEnabled(true).setMaxRedirects(param.getMaxRedirect())
                .setProxy(resolveProxy(param.getProxy())).build();
    }

    static Pair<Integer, String> resolveErrCodeAndMsg(Throwable ex) {
        Preconditions.checkArgument(ex != null);
        if (ex instanceof ExecutionException)
            ex = ex.getCause();
        if (ex instanceof HttpNot200Exception) {
            return new Pair<>(22,
                    String.format("Non-200 status code returned: %s.", ex.getMessage()));
        }
        if (ex instanceof DownloadSizeExceedsLimitException) {
            return new Pair<>(63,
                    String.format("Maximum file size exceeded: %s.", ex.getMessage()));
        }
        if (ex instanceof ConnectException) {
            return new Pair<>(7, String.format("Failed to connect to host: %s.", ex.getMessage()));
        }
        if (ex instanceof TimeoutException || ex instanceof SocketTimeoutException) {
            return new Pair<>(28, String.format("Operation timeout: %s.", ex.getMessage()));
        }
        if (ex instanceof ConnectionClosedException) {
            return new Pair<>(18, String.format(
                    "Partial file. Only a part of the file was transferred: %s.", ex.getMessage()));
        }

        return new Pair<>(-3, ExceptionUtils.printAsString(ex));
    }

    private static CloseableHttpAsyncClient getClient(AsyncHttpClientDownloadParam param) {
        if (client != null)
            return client;
        synchronized (AsyncHttpClientDownloadUtil.class) {
            if (client != null)
                return client;
            try {
                CloseableHttpAsyncClient ret = HttpAsyncClients.custom()
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .setConnectionReuseStrategy(NoConnectionReuseStrategy.INSTANCE)
                        .setConnectionManager(createNHttpClientConnectionManager(param))
                        .setConnectionManagerShared(false)
                        .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
                        .setSSLContext(createSslContext())
                        .setUserAgent(
                                param.getUserAgent() != null ? param.getUserAgent() : "HttpAsyncClient")
                        .build();
                ret.start();
                client = ret;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return client;
        }
    }

    private static HttpHost resolveProxy(String proxy) {
        // hzss_complex:8fzpSV6pzTe5ApYK@172.21.1.3:10041,
        if (StringUtils.isBlank(proxy))
            return null;
        Pair<AuthScope, Credentials> scopeAndCreds = resolveAuthInfo(proxy);
        if (!addedToCreds.contains(scopeAndCreds.getFirst()) && scopeAndCreds.getSecond() != null) {
            credentialsProvider.setCredentials(scopeAndCreds.getFirst(), scopeAndCreds.getSecond());
            addedToCreds.add(scopeAndCreds.getFirst());
        }
        return scopeAndCreds.getFirst().getOrigin();
    }

    private static Pair<AuthScope, Credentials> resolveAuthInfo(String proxy) {
        if (StringUtils.countMatches(proxy, ':') == 2
                && StringUtils.countMatches(proxy, '@') == 1) {
            return new Pair<>(
                    new AuthScope(HttpHost.create(StringUtils.substringAfterLast(proxy, "@"))),
                    new UsernamePasswordCredentials(StringUtils.substringBeforeLast(proxy, "@")));
        } else if (!StringUtils.contains(proxy, '@') && StringUtils.countMatches(proxy, ':') == 1) {
            return new Pair<>(new AuthScope(HttpHost.create(proxy)), null);
        } else {
            throw new IllegalArgumentException(String.format(
                    "Illegal proxy string: %s, proxy string must be of format: [username:password@]ip:port",
                    proxy));
        }
    }

    private static SSLContext createSslContext() throws Exception {
        X509TrustManager tm = new X509TrustManager() {

            public void checkClientTrusted(X509Certificate[] xcs, String string) {
            }

            public void checkServerTrusted(X509Certificate[] xcs, String string) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{tm}, null);
        return ctx;
    }

    private static NHttpClientConnectionManager createNHttpClientConnectionManager(AsyncHttpClientDownloadParam param) throws IOReactorException {
        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(IOReactorConfig.custom()
                .setSoKeepAlive(false).setConnectTimeout(param.getConnectTimeout() * 1000)
                .setSoTimeout((param.getTotalTimeout() - param.getConnectTimeout()) * 1000)
                .setTcpNoDelay(true).build());
        PoolingNHttpClientConnectionManager connectionManager = new PoolingNHttpClientConnectionManager(
                ioReactor);
        connectionManager.setDefaultMaxPerRoute(1024);
        connectionManager.setMaxTotal(1024 * 1024 * 1024);
        return connectionManager;
    }

    public static void shutdown() {
        Runner.tryExec(() -> {
            try {
                client.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Runner.tryExec(multiplexer::destroy);
        Runner.shutdownThreadPool(respReaderPool, 5);
    }
}
