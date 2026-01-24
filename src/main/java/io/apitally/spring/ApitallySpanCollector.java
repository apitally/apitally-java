package io.apitally.spring;

import io.apitally.common.SpanCollector;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApitallySpanCollector implements SpanProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ApitallySpanCollector.class);
    private static final ApitallySpanCollector INSTANCE = new ApitallySpanCollector();
    private volatile SpanCollector delegate;

    public static ApitallySpanCollector getInstance() {
        return INSTANCE;
    }

    private ApitallySpanCollector() {}

    public void setDelegate(SpanCollector spanCollector) {
        this.delegate = spanCollector;
        if (spanCollector != null) {
            initializeTracer(spanCollector);
        }
    }

    private void initializeTracer(SpanCollector spanCollector) {
        try {
            SdkTracerProvider provider = SdkTracerProvider.builder().addSpanProcessor(this).build();
            OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
            Tracer tracer;
            try {
                GlobalOpenTelemetry.set(sdk);
                tracer = sdk.getTracer("apitally");
            } catch (IllegalStateException e) {
                tracer = GlobalOpenTelemetry.getTracer("apitally");
            }
            spanCollector.setTracer(tracer);
        } catch (Exception e) {
            logger.warn("Failed to setup OpenTelemetry tracer", e);
        }
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        if (delegate != null) {
            delegate.onStart(parentContext, span);
        }
    }

    @Override
    public boolean isStartRequired() {
        return delegate != null && delegate.isStartRequired();
    }

    @Override
    public void onEnd(ReadableSpan span) {
        if (delegate != null) {
            delegate.onEnd(span);
        }
    }

    @Override
    public boolean isEndRequired() {
        return delegate != null && delegate.isEndRequired();
    }

    public SpanCollector.SpanHandle startCollection() {
        return delegate != null ? delegate.startCollection() : null;
    }
}
