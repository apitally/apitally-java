package io.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpanData {
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final String kind;
    private final long startTime;
    private final long endTime;
    private final String status;
    private final Map<String, Object> attributes;

    public SpanData(
            String spanId,
            String parentSpanId,
            String name,
            String kind,
            long startTime,
            long endTime,
            String status,
            Map<String, Object> attributes) {
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.kind = kind;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.attributes = attributes;
    }

    @JsonProperty("span_id")
    public String getSpanId() {
        return spanId;
    }

    @JsonProperty("parent_span_id")
    public String getParentSpanId() {
        return parentSpanId;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("kind")
    public String getKind() {
        return kind;
    }

    @JsonProperty("start_time")
    public long getStartTime() {
        return startTime;
    }

    @JsonProperty("end_time")
    public long getEndTime() {
        return endTime;
    }

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    @JsonProperty("attributes")
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
