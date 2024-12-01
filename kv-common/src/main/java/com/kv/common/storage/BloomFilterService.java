package com.kv.common.storage;

public interface BloomFilterService {
    /**
     * 添加key到布隆过滤器
     */
    void add(byte[] key);

    /**
     * 检查key是否可能存在
     */
    boolean mightContain(byte[] key);
}
