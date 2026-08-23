package com.vacaro.benchlab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchlab")
public class BenchLabProperties {

    private final Queue queue = new Queue();
    private final Worker worker = new Worker();
    private final Limits limits = new Limits();

    public Queue getQueue() {
        return queue;
    }

    public Worker getWorker() {
        return worker;
    }

    public Limits getLimits() {
        return limits;
    }

    public static class Queue {

        private String exchange = "benchlab.run.exchange";
        private String runRequestedRoutingKey = "benchlab.run.requested";
        private String runRequestedQueue = "benchlab.run.requested.q";

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRunRequestedRoutingKey() {
            return runRequestedRoutingKey;
        }

        public void setRunRequestedRoutingKey(String runRequestedRoutingKey) {
            this.runRequestedRoutingKey = runRequestedRoutingKey;
        }

        public String getRunRequestedQueue() {
            return runRequestedQueue;
        }

        public void setRunRequestedQueue(String runRequestedQueue) {
            this.runRequestedQueue = runRequestedQueue;
        }
    }

    public static class Worker {

        private String callbackToken = "benchlab-internal-token";

        public String getCallbackToken() {
            return callbackToken;
        }

        public void setCallbackToken(String callbackToken) {
            this.callbackToken = callbackToken;
        }
    }

    public static class Limits {

        private int maxSourceBytes = 65_536;
        private int maxTimeoutMs = 30_000;
        private int maxMemoryMb = 512;
        private double maxCpuLimit = 2.0;
        private int maxIterations = 10;
        private int maxWarmupIterations = 3;
        private long maxDatasetSize = 25_000_000L;
        private long maxOutstandingRunsPerUser = 32L;

        public int getMaxSourceBytes() {
            return maxSourceBytes;
        }

        public void setMaxSourceBytes(int maxSourceBytes) {
            this.maxSourceBytes = maxSourceBytes;
        }

        public int getMaxTimeoutMs() {
            return maxTimeoutMs;
        }

        public void setMaxTimeoutMs(int maxTimeoutMs) {
            this.maxTimeoutMs = maxTimeoutMs;
        }

        public int getMaxMemoryMb() {
            return maxMemoryMb;
        }

        public void setMaxMemoryMb(int maxMemoryMb) {
            this.maxMemoryMb = maxMemoryMb;
        }

        public double getMaxCpuLimit() {
            return maxCpuLimit;
        }

        public void setMaxCpuLimit(double maxCpuLimit) {
            this.maxCpuLimit = maxCpuLimit;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }

        public int getMaxWarmupIterations() {
            return maxWarmupIterations;
        }

        public void setMaxWarmupIterations(int maxWarmupIterations) {
            this.maxWarmupIterations = maxWarmupIterations;
        }

        public long getMaxDatasetSize() {
            return maxDatasetSize;
        }

        public void setMaxDatasetSize(long maxDatasetSize) {
            this.maxDatasetSize = maxDatasetSize;
        }

        public long getMaxOutstandingRunsPerUser() {
            return maxOutstandingRunsPerUser;
        }

        public void setMaxOutstandingRunsPerUser(long maxOutstandingRunsPerUser) {
            this.maxOutstandingRunsPerUser = maxOutstandingRunsPerUser;
        }
    }
}
