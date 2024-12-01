package com.kv.server.storage;
import com.kv.common.storage.BloomFilterService;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

public class BloomFilterImpl implements BloomFilterService {
    private final BloomFilter<byte[]> filter;

    public BloomFilterImpl(int expectedInsertions, double fpp) {
        this.filter = BloomFilter.create(
            Funnels.byteArrayFunnel(),
            expectedInsertions,
            fpp);
    }

    @Override
    public void add(byte[] key) {
        filter.put(key);
    }

    @Override
    public boolean mightContain(byte[] key) {
        return filter.mightContain(key);
    }
}