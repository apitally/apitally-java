package io.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResourceUsage {
    private final double cpuPercent;
    private final long memoryRss;

    public ResourceUsage(double cpuPercent, long memoryRss) {
        this.cpuPercent = cpuPercent;
        this.memoryRss = memoryRss;
    }

    @JsonProperty("cpu_percent")
    public double getCpuPercent() {
        return cpuPercent;
    }

    @JsonProperty("memory_rss")
    public long getMemoryRss() {
        return memoryRss;
    }
}
