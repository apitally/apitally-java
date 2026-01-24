package io.apitally.common;

import io.apitally.common.dto.SpanData;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpanCollector implements SpanProcessor {
    private final boolean enabled;
    private volatile Tracer tracer;
    private final Map<String, Set<String>> includedSpanIds = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<SpanData>> collectedSpans =
            new ConcurrentHashMap<>();

    public SpanCollector(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    public SpanHandle startCollection() {
        if (!enabled || tracer == null) {
            return null;
        }

        Span span = tracer.spanBuilder("root").setSpanKind(SpanKind.INTERNAL).startSpan();
        Scope scope = Context.current().with(span).makeCurrent();
        SpanContext spanContext = span.getSpanContext();
        String traceId = spanContext.getTraceId();

        includedSpanIds.put(traceId, ConcurrentHashMap.newKeySet());
        includedSpanIds.get(traceId).add(spanContext.getSpanId());
        collectedSpans.put(traceId, new ConcurrentLinkedQueue<>());

        return new SpanHandle(traceId, span, scope, this);
    }

    List<SpanData> getAndClearSpans(String traceId) {
        if (traceId == null) {
            return null;
        }

        includedSpanIds.remove(traceId);
        ConcurrentLinkedQueue<SpanData> spans = collectedSpans.remove(traceId);
        return spans != null ? new ArrayList<>(spans) : null;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        if (!enabled) {
            return;
        }

        SpanContext spanContext = span.getSpanContext();
        String traceId = spanContext.getTraceId();
        String spanId = spanContext.getSpanId();

        Set<String> included = includedSpanIds.get(traceId);
        if (included == null) {
            return;
        }

        SpanContext parentSpanContext = span.getParentSpanContext();
        if (parentSpanContext.isValid() && included.contains(parentSpanContext.getSpanId())) {
            included.add(spanId);
        }
    }

    @Override
    public boolean isStartRequired() {
        return enabled;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        if (!enabled) {
            return;
        }

        SpanContext spanContext = span.getSpanContext();
        String traceId = spanContext.getTraceId();
        String spanId = spanContext.getSpanId();

        Set<String> included = includedSpanIds.get(traceId);
        if (included == null || !included.contains(spanId)) {
            return;
        }

        SpanData data = serializeSpan(span);
        ConcurrentLinkedQueue<SpanData> spans = collectedSpans.get(traceId);
        if (spans != null) {
            spans.add(data);
        }
    }

    @Override
    public boolean isEndRequired() {
        return enabled;
    }

    private SpanData serializeSpan(ReadableSpan span) {
        io.opentelemetry.sdk.trace.data.SpanData spanData = span.toSpanData();
        SpanContext spanContext = spanData.getSpanContext();
        SpanContext parentSpanContext = spanData.getParentSpanContext();

        String parentSpanId = null;
        if (parentSpanContext.isValid()) {
            parentSpanId = parentSpanContext.getSpanId();
        }

        String status = null;
        if (spanData.getStatus().getStatusCode() != StatusCode.UNSET) {
            status = spanData.getStatus().getStatusCode().name();
        }

        Map<String, Object> attributes = null;
        if (!spanData.getAttributes().isEmpty()) {
            Map<String, Object> attrMap = new HashMap<>();
            spanData.getAttributes().forEach((key, value) -> attrMap.put(key.getKey(), value));
            attributes = attrMap;
        }

        return new SpanData(
                spanContext.getSpanId(),
                parentSpanId,
                spanData.getName(),
                spanData.getKind().name(),
                spanData.getStartEpochNanos(),
                spanData.getEndEpochNanos(),
                status,
                attributes);
    }

    public static class SpanHandle {
        private final String traceId;
        private final Span span;
        private final Scope scope;
        private final SpanCollector collector;

        SpanHandle(String traceId, Span span, Scope scope, SpanCollector collector) {
            this.traceId = traceId;
            this.span = span;
            this.scope = scope;
            this.collector = collector;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setName(String name) {
            if (span != null) {
                span.updateName(name);
            }
        }

        public List<SpanData> end() {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
            return collector.getAndClearSpans(traceId);
        }
    }

    void resetForTest() {
        this.tracer = null;
        this.includedSpanIds.clear();
        this.collectedSpans.clear();
    }
}
