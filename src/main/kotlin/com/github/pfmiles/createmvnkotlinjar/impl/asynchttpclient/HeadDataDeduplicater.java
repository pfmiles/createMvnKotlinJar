package com.github.pfmiles.createmvnkotlinjar.impl.asynchttpclient;

/**
 * 头部数据去重器
 * <p>
 * 根据头部数据，返回对应的之前已有数据的uri, 若没有则返回null
 *
 * @author pf-miles
 */
public interface HeadDataDeduplicater {
    /**
     * 根据头部数据，获取之前以下载过的该文件数据的字符串表示，该表示可以是个url，也可以是某种能够唯一标识该文件的String
     *
     * @param headData 头部数据, 长度为下载参数中定义的"headDataLength"，也可能小于该长度，当整个下载数据都没有headDataLength长时
     * @return 已下载过的文件的字符串表示，如url或其它唯一性String值，该值需要能够被当前正处理的业务逻辑场景所理解
     */
    String dedup(byte[] headData);
}
