package com.vacaro.benchlab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benchlab")
public class BenchLabProperties {

    private final Queue queue = new Queue();
    private final Worker worker = new Worker();

    public Queue getQueue() {
        return queue;
    }

    public Worker getWorker() {
        return worker;
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
}
