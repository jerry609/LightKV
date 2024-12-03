package com.kv.thrift;

import java.util.concurrent.TimeUnit;

public class KVServiceConfig {
    // Operation timeouts
    private final long operationTimeout;
    private final TimeUnit operationTimeoutUnit;

    // Thread pool settings
    private final int minThreads;
    private final int maxThreads;
    private final int queueSize;

    // Key-Value constraints
    private final int maxKeyLength;
    private final int maxValueSize;

    // Performance settings
    private final int batchSize;
    private final boolean enableCompression;

    private KVServiceConfig(Builder builder) {
        this.operationTimeout = builder.operationTimeout;
        this.operationTimeoutUnit = builder.operationTimeoutUnit;
        this.minThreads = builder.minThreads;
        this.maxThreads = builder.maxThreads;
        this.queueSize = builder.queueSize;
        this.maxKeyLength = builder.maxKeyLength;
        this.maxValueSize = builder.maxValueSize;
        this.batchSize = builder.batchSize;
        this.enableCompression = builder.enableCompression;
    }

    public long getOperationTimeout() {
        return operationTimeout;
    }

    public TimeUnit getOperationTimeoutUnit() {
        return operationTimeoutUnit;
    }

    public int getMinThreads() {
        return minThreads;
    }

    public int getMaxThreads() {
        return maxThreads;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public int getMaxKeyLength() {
        return maxKeyLength;
    }

    public int getMaxValueSize() {
        return maxValueSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isEnableCompression() {
        return enableCompression;
    }

    public static class Builder {
        // Default values
        private long operationTimeout = 10000;
        private TimeUnit operationTimeoutUnit = TimeUnit.MILLISECONDS;
        private int minThreads = 4;
        private int maxThreads = Runtime.getRuntime().availableProcessors() * 2;
        private int queueSize = 1000;
        private int maxKeyLength = 256;
        private int maxValueSize = 1024 * 1024; // 1MB
        private int batchSize = 100;
        private boolean enableCompression = false;

        public Builder operationTimeout(long timeout, TimeUnit unit) {
            this.operationTimeout = timeout;
            this.operationTimeoutUnit = unit;
            return this;
        }

        public Builder minThreads(int minThreads) {
            this.minThreads = minThreads;
            return this;
        }

        public Builder maxThreads(int maxThreads) {
            this.maxThreads = maxThreads;
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder maxKeyLength(int maxKeyLength) {
            this.maxKeyLength = maxKeyLength;
            return this;
        }

        public Builder maxValueSize(int maxValueSize) {
            this.maxValueSize = maxValueSize;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder enableCompression(boolean enableCompression) {
            this.enableCompression = enableCompression;
            return this;
        }

        public KVServiceConfig build() {
            validate();
            return new KVServiceConfig(this);
        }

        private void validate() {
            if (operationTimeout <= 0) {
                throw new IllegalArgumentException("Operation timeout must be positive");
            }
            if (minThreads <= 0) {
                throw new IllegalArgumentException("Minimum thread count must be positive");
            }
            if (maxThreads < minThreads) {
                throw new IllegalArgumentException("Maximum thread count must be >= minimum thread count");
            }
            if (queueSize <= 0) {
                throw new IllegalArgumentException("Queue size must be positive");
            }
            if (maxKeyLength <= 0) {
                throw new IllegalArgumentException("Maximum key length must be positive");
            }
            if (maxValueSize <= 0) {
                throw new IllegalArgumentException("Maximum value size must be positive");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("Batch size must be positive");
            }
            if (operationTimeoutUnit == null) {
                throw new IllegalArgumentException("Operation timeout unit cannot be null");
            }
        }
    }
}