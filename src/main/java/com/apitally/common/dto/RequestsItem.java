package com.apitally.common.dto;

import java.util.Map;

public final class RequestsItem {
        private final String consumer;
        private final String method;
        private final String path;
        private final int statusCode;
        private final int requestCount;
        private final long requestSizeSum;
        private final long responseSizeSum;
        private final Map<Integer, Integer> responseTimes;
        private final Map<Integer, Integer> requestSizes;
        private final Map<Integer, Integer> responseSizes;

        public RequestsItem(String consumer, String method, String path, int statusCode, int requestCount,
                        long requestSizeSum, long responseSizeSum, Map<Integer, Integer> responseTimes,
                        Map<Integer, Integer> requestSizes, Map<Integer, Integer> responseSizes) {
                this.consumer = consumer;
                this.method = method;
                this.path = path;
                this.statusCode = statusCode;
                this.requestCount = requestCount;
                this.requestSizeSum = requestSizeSum;
                this.responseSizeSum = responseSizeSum;
                this.responseTimes = responseTimes;
                this.requestSizes = requestSizes;
                this.responseSizes = responseSizes;
        }

        public String getConsumer() {
                return consumer;
        }

        public String getMethod() {
                return method;
        }

        public String getPath() {
                return path;
        }

        public int getStatusCode() {
                return statusCode;
        }

        public int getRequestCount() {
                return requestCount;
        }

        public long getRequestSizeSum() {
                return requestSizeSum;
        }

        public long getResponseSizeSum() {
                return responseSizeSum;
        }

        public Map<Integer, Integer> getResponseTimes() {
                return responseTimes;
        }

        public Map<Integer, Integer> getRequestSizes() {
                return requestSizes;
        }

        public Map<Integer, Integer> getResponseSizes() {
                return responseSizes;
        }
}
