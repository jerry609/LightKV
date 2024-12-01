package com.kv.server.storage;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.common.hash.Hashing;
import com.kv.common.storage.BloomFilterService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;

public class BloomFilterImpl2 implements BloomFilterService {
    private final int size;
    private final int hashFunctions;
    private final ArrayList<Long> bitSet;

    public BloomFilterImpl2(long expectedInsertions, double fpp) {
        this.size = calculateBitMapSize(expectedInsertions, fpp);
        this.hashFunctions = calculateOptimalK(expectedInsertions, this.size);
        this.bitSet = new ArrayList<>(Collections.nCopies(this.size, 0L));
    }

    @Override
    public void add(byte[] key) {
        for (int i = 0; i < hashFunctions; i++) {
            int hash = murmurHash(key, i);
            bitSet.set(hash, bitSet.get(hash) + 1);
        }
    }

    @Override
    public boolean mightContain(byte[] key) {
        for (int i = 0; i < hashFunctions; i++) {
            int hash = murmurHash(key, i);
            if (bitSet.get(hash) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * @attention 一定要先确保有这个key才能够调用删除
     */
    public void delete(byte[] key) {
        for (int i = 0; i < hashFunctions; i++) {
            int hash = murmurHash(key, i);
            bitSet.set(hash, bitSet.get(hash) - 1);
        }
    }

    private int murmurHash(byte[] value, int seed) {
        int temp = Hashing.murmur3_32_fixed(seed)
                .hashBytes(value)
                .asInt();
        return Math.abs(temp % this.size);
    }

    public static int calculateBitMapSize(long expectedInsertions, double fpp) {
        if (fpp == 0) {
            fpp = Double.MIN_VALUE;
        }
        return (int)Math.min((-expectedInsertions * Math.log(fpp) / (Math.log(2) * Math.log(2))), 100000);
    }

    public static int calculateOptimalK(long expectedInsertions, int bitSetSize) {
        return Math.max(1, (int)Math.round((double)bitSetSize / expectedInsertions * Math.log(2)));
    }
}
