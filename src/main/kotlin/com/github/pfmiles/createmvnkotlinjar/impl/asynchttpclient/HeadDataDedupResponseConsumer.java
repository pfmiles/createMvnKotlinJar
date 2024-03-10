package com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient;

import com.google.common.base.Preconditions;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentDecoderChannel;
import org.apache.http.nio.FileContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 为头部数据去重下载专门定制，当头部数据去重开启时，该consumer会先从response body中读取headDataLength个byte作为头部数据，然后使用headDataDeduplicater逻辑针对该头部数据做一次查询，
 * 若该查询返回null，则将继续读取剩余response数据直至处理完毕
 * 若该查询有返回值，则将放弃后续数据的读取，仅保存当前已读取到的头部数据bytes及headDataDeduplicater逻辑查询的返回值
 * 若头部数据去重功能关闭，则直接下载目标文件
 * 下载时实现了zero copy下载，代码参考自：org.apache.http.nio.client.methods.ZeroCopyConsumer
 *
 * @author pf-miles
 */
public class HeadDataDedupResponseConsumer extends
        AbstractAsyncResponseConsumer<AsyncHttpClientDownloadResult> {

    // 在读取头部数据时的buffer size, bytes
    private static final int READ_BUF_SIZE = 16 * 1024;
    // 在执行zero-copy下载时的下载步长, bytes，该步长决定了下载过程中动态检查最大下载大小的频率
    private static final long DOWNLOAD_STEP = 16 * 1024 * 1024;

    // 下载Url
    private final String downloadUrl;
    // 应读取头部数据的长度
    private final int headDataLength;
    // 头部数据查询器
    private final HeadDataDeduplicater headDataDeduplicater;
    private final File targetFile;
    // 允许下载的最大文件大小限制, bytes
    private final long maxFileSize;
    private final boolean dedupOn;

    private final RandomAccessFile accessFile;
    private FileChannel fileChannel;
    // 当前已下载的数据bytes的count, 也是下一个写入byte的index
    private long downloadIdx = -1;
    private ByteBuffer buf;
    // 读过程中的head数据总缓存
    private ByteArrayOutputStream bos;
    // 使用头部数据对headDataDeduplicater发起调用后的返回值
    private String dedupResult;
    // headDedup逻辑是已执行过
    private boolean headDedupExed;

    private HttpResponse response;
    private HttpEntity httpEntity;
    private ContentType contentType;
    private Header contentEncoding;
    private long contentLength;

    /**
     * Constructor
     *
     * @param downloadUrl          下载链接
     * @param headDataLength       定义头部数据的长度, bytes
     * @param headDataDeduplicater 头部数据去重器
     * @param targetFile           位于本地磁盘的下载目标文件
     * @param dedupOn              是否开启头部数据去重
     */
    public HeadDataDedupResponseConsumer(String downloadUrl, int headDataLength,
                                         HeadDataDeduplicater headDataDeduplicater, File targetFile,
                                         long maxFileSize, boolean dedupOn) {
        super();
        Preconditions.checkArgument(downloadUrl != null, "downloadUrl must be specified.");
        if (dedupOn) {
            Preconditions.checkArgument(headDataLength > 0,
                    "headDataLength must be greater than 0.");
            Preconditions.checkArgument(headDataDeduplicater != null,
                    "headDataDeduplicater must not be null.");
        }
        Preconditions.checkArgument(targetFile != null, "downloadedFile must not be null");
        Preconditions.checkArgument(maxFileSize > 16 * 1024,
                "maxFileSize must be greater than 16KB, nonsense otherwise.");

        this.downloadUrl = downloadUrl.trim();
        this.headDataLength = headDataLength;
        this.headDataDeduplicater = headDataDeduplicater;

        this.targetFile = targetFile;
        if (!this.targetFile.exists()) {
            try {
                this.targetFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            this.accessFile = new RandomAccessFile(this.targetFile, "rw");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        this.maxFileSize = maxFileSize;
        this.dedupOn = dedupOn;
    }

    // 1.response刚到达，可取到状态行和headers
    @Override
    protected void onResponseReceived(final HttpResponse response) {
        StatusLine statusLine = response.getStatusLine();
        // 接受1xx, 2xx, 3xx状态
        if (statusLine.getStatusCode() >= HttpStatus.SC_BAD_REQUEST) {
            throw new HttpNot200Exception(
                    String.format("Response returned non-200 code: %s, errMsg: %s.",
                            statusLine.getStatusCode(), statusLine.getReasonPhrase()));
        }
        this.response = response;
    }

    // 2.仅当onResponseReceived得到的response对象拥有entity时才会被调用, 此时能取到contentType、encoding、length等信息
    @Override
    protected void onEntityEnclosed(final HttpEntity entity, final ContentType contentType) {
        this.httpEntity = entity;
        this.contentType = contentType;
        this.contentEncoding = entity.getContentEncoding();
        this.contentLength = entity.getContentLength();

        if (this.contentLength > 0 && this.contentLength > this.maxFileSize)
            throw new DownloadSizeExceedsLimitException(String.format(
                    "Download file size exceeds limit, downloading file size: %s, size limit: %s.",
                    this.contentLength, this.maxFileSize));

        this.fileChannel = this.accessFile.getChannel();
        this.downloadIdx = 0;

        this.buf = ByteBuffer.allocate(READ_BUF_SIZE);
        this.bos = new ByteArrayOutputStream(this.headDataLength * 2);
    }

    // 3.consume到达的entity数据, 若entity采用chunked编码传输，则根据多个chunk的不同到达时机，该方法可能会被调用多次
    @Override
    protected void onContentReceived(final ContentDecoder decoder,
                                     final IOControl ioctrl) throws IOException {
        if (!headDedupExed) {
            if (dedupOn) {
                // 1.读取头部数据暂存到bos，直到entity被读完或当前chunk被读完或已读够headDataLength个bytes(decoder没有读完)
                int lastRead = readDataUntilEOForChunkEndOrExceedsHeadLength(decoder);

                if (this.downloadIdx >= this.headDataLength) {
                    // 2.若已读够，则先暂停io, 计算cachedFileKey, 命中则取消io，直接返回；未命中则继续io读取后续所有数据到target文件
                    ioctrl.suspendInput();
                    byte[] headData = this.bos.toByteArray();
                    this.dedupResult = dedup(headData);
                    if (dedupResult != null) {
                        ioctrl.shutdown();
                    } else {
                        // 将已读的head数据和所有后续数据一并写入targetFile
                        this.fileChannel.write(ByteBuffer.wrap(headData));
                        ioctrl.requestInput();
                        this.writeAllDataToFile(decoder, ioctrl);
                    }
                } else {
                    // 3.decoder已被读完但head数据还未读够
                    // 如果已经到达EOF，则说明整个entity已读完，本次下载的entity大小还不如headDataLength大，直接将全部entity数据用作计算headDedup
                    if (decoder.isCompleted() || lastRead == -1) {
                        byte[] headData = this.bos.toByteArray();
                        // 总长度为0的下载文件不参与dedup
                        if (ArrayUtils.isNotEmpty(headData))
                            this.dedupResult = dedup(headData);
                        if (this.dedupResult == null) {
                            // 未命中，将数据写target文件
                            this.fileChannel.write(ByteBuffer.wrap(headData));
                        }
                    } else {
                        // 未到达EOF，此时必定是当前chunk读完了，退出方法等待下一次调用(下一个chunk到达)
                        Preconditions.checkState(lastRead == 0,
                                "Decoder not chunk-end but headData-reading is not enough, impossible, there must be a bug!");
                    }
                }
            } else {
                // 未开启头部数据去重，直接往targetFile写入所有下载数据
                this.writeAllDataToFile(decoder, ioctrl);
            }
        } else {
            if (dedupResult != null) {
                // dedup逻辑已执行，且得到了可用的result，则丢弃所有后续数据，关闭网络io
                ioctrl.shutdown();
            } else {
                // dedup逻辑已执行但没有找到可用结果，将后续数据持续写入targetFile
                this.writeAllDataToFile(decoder, ioctrl);
            }
        }
    }

    // 读取头部数据暂存到bos，直到entity被读完或当前chunk被读完或已读够headDataLength个bytes(decoder没有读完), 返回最后一次读取的byte数(-1, 0 或 正数)
    private int readDataUntilEOForChunkEndOrExceedsHeadLength(ContentDecoder decoder) throws IOException {
        int read = -2;
        while (!decoder.isCompleted() && this.downloadIdx < this.headDataLength) {
            this.buf.clear();
            read = decoder.read(this.buf);
            // -1: EOF, 0: 当前chunk已读完
            if (read != -1 && read != 0) {
                this.buf.flip();
                while (this.buf.hasRemaining()) {
                    this.bos.write(this.buf.get());
                }
                this.downloadIdx += read;
            } else {
                // EOF(-1) or end of current chunk(0)
                break;
            }
        }
        return read;
    }

    private String dedup(byte[] bytes) {
        Preconditions.checkState(ArrayUtils.isNotEmpty(bytes),
                "Head data for dedup compute must not be empty.");
        byte[] d;
        if (bytes.length >= this.headDataLength) {
            d = new byte[this.headDataLength];
            System.arraycopy(bytes, 0, d, 0, this.headDataLength);
        } else {
            d = bytes;
        }
        this.headDedupExed = true;
        return this.headDataDeduplicater.dedup(d);
    }

    // 将本次decoder数据全部写入结果文件，zero-copy, 本方法可能随着多个chunks的到达被onContentReceived多次调用
    private void writeAllDataToFile(ContentDecoder decoder, IOControl ioctrl) throws IOException {
        long transferred = -2;
        // decoder.isCompleted()或-1说明EOF，0说明本次chunk数据传输结束
        while (!decoder.isCompleted() && transferred != 0 && transferred != -1) {
            if (decoder instanceof FileContentDecoder) {
                transferred = ((FileContentDecoder) decoder).transfer(this.fileChannel,
                        this.downloadIdx, DOWNLOAD_STEP);
            } else {
                transferred = this.fileChannel.transferFrom(new ContentDecoderChannel(decoder),
                        this.downloadIdx, DOWNLOAD_STEP);
            }
            if (transferred > 0) {
                this.downloadIdx += transferred;
            }
            if (this.downloadIdx > this.maxFileSize) {
                ioctrl.shutdown();
                throw new DownloadSizeExceedsLimitException(String.format(
                        "Download file size exceeds limit, current downloaded size: %s, size limit: %s.",
                        this.downloadIdx, this.maxFileSize));
            }
        }
    }

    // 4.在整个response的所有数据完全处理完毕后调用, 构造最终返回值；若是file://开头的url则表示未命中头部数据去重规则，完整下载了文件，然后返回了文件url；若是其它字符串，则表示命中了去重规则，返回了被cache的历史数据的字符串表示
    @Override
    protected AsyncHttpClientDownloadResult buildResult(final HttpContext context) {
        if (this.httpEntity == null)
            throw new NoDownloadEntityFoundException();
        AsyncHttpClientDownloadResult ret = new AsyncHttpClientDownloadResult();
        ret.setHttpResponse(this.response);
        if (this.dedupResult != null) {
            ret.setCachedFileKey(this.dedupResult);
        } else {
            ret.setFile(this.targetFile);
            // 设置HttpResponse的entity
            FileEntity entity = new FileEntity(this.targetFile, this.contentType);
            if (this.contentEncoding != null)
                entity.setContentEncoding(this.contentEncoding);
            if (this.contentType != null)
                entity.setContentType(this.contentType.toString());
            this.response.setEntity(entity);
            // 完整下载完文件后，contentLength以实际下载为准
            if (this.contentLength != this.downloadIdx)
                this.contentLength = this.downloadIdx;
        }
        ret.setRemoteFileName(resolveFileName(this.downloadUrl, response));
        ret.setContentType(this.contentType);
        ret.setContentEncoding(this.contentEncoding);
        ret.setFileSize(this.contentLength);
        return ret;
    }

    // 5.清理
    @Override
    protected void releaseResources() {
        IOUtils.closeQuietly(this.fileChannel);
        IOUtils.closeQuietly(this.accessFile);
        IOUtils.closeQuietly(this.bos);
    }

    private static String resolveFileName(String url, HttpResponse response) {
        String filename = resolveNameFromHeader(response);
        if (StringUtils.isBlank(filename)) {
            filename = resolveFileNameFromURL(url);
        }
        if (StringUtils.isNotBlank(filename) && filename.length() > 200) {
            filename = filename.substring(0, 200);
        }
        return filename;
    }

    public static String resolveFileNameFromURL(String url) {
        try {
            URL u = new URL(url);
            File file = new File(u.getFile());
            return file.getName();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private static String resolveNameFromHeader(HttpResponse response) {
        Header contentHeader = response.getFirstHeader("Content-Disposition");
        if (contentHeader == null) {
            return null;
        }

        HeaderElement[] values = contentHeader.getElements();
        if (ArrayUtils.isEmpty(values)) {
            return null;
        }

        for (HeaderElement he : values) {
            NameValuePair param = he.getParameterByName("filename");
            if (param == null) {
                continue;
            }
            return param.getValue();
        }

        return null;
    }

    public boolean isDedupOn() {
        return dedupOn;
    }

    public String getDedupResult() {
        return dedupResult;
    }

    public boolean isHeadDedupExed() {
        return headDedupExed;
    }
}
