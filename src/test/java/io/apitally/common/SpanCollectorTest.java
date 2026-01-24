package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.apitally.common.dto.SpanData;
import io.apitally.spring.ApitallySpanCollector;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpanCollectorTest {

    private SpanCollector collector;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        ApitallySpanCollector.getInstance().setDelegate(null);
    }

    @AfterEach
    void tearDown() {
        if (collector != null) {
            collector.resetForTest();
        }
        ApitallySpanCollector.getInstance().setDelegate(null);
        GlobalOpenTelemetry.resetForTest();
    }

    private SpanCollector createAndRegisterCollector(boolean enabled) {
        collector = new SpanCollector(enabled);
        ApitallySpanCollector.getInstance().setDelegate(collector);
        return collector;
    }

    @Test
    void testDisabledCollector() {
        SpanCollector collector = createAndRegisterCollector(false);

        SpanCollector.SpanHandle handle = collector.startCollection();
        assertNull(handle);
    }

    @Test
    void testEnabledCollector() {
        SpanCollector collector = createAndRegisterCollector(true);

        SpanCollector.SpanHandle handle = collector.startCollection();
        assertNotNull(handle);
        assertNotNull(handle.getTraceId());
        assertEquals(32, handle.getTraceId().length());

        List<SpanData> spans = handle.end();
        assertNotNull(spans);
        assertEquals(1, spans.size());
        assertEquals("root", spans.get(0).getName());
        assertNull(spans.get(0).getParentSpanId());
    }

    @Test
    void testCollectorWithChildSpans() {
        SpanCollector collector = createAndRegisterCollector(true);

        SpanCollector.SpanHandle handle = collector.startCollection();
        Tracer tracer = GlobalOpenTelemetry.getTracer("test");

        Span child1 = tracer.spanBuilder("child1").startSpan();
        child1.end();

        Span child2 = tracer.spanBuilder("child2").startSpan();
        child2.end();

        handle.setName("TestController.getTest");

        List<SpanData> spans = handle.end();
        assertNotNull(spans);
        assertEquals(3, spans.size());

        Set<String> spanNames = spans.stream().map(SpanData::getName).collect(Collectors.toSet());
        assertTrue(spanNames.contains("TestController.getTest"));
        assertTrue(spanNames.contains("child1"));
        assertTrue(spanNames.contains("child2"));

        SpanData rootSpan =
                spans.stream()
                        .filter(s -> s.getName().equals("TestController.getTest"))
                        .findFirst()
                        .orElse(null);
        assertNotNull(rootSpan);
        assertNull(rootSpan.getParentSpanId());

        SpanData childSpan =
                spans.stream().filter(s -> s.getName().equals("child1")).findFirst().orElse(null);
        assertNotNull(childSpan);
        assertEquals(rootSpan.getSpanId(), childSpan.getParentSpanId());
    }

    @Test
    void testDoesNotCollectUnrelatedSpans() {
        SpanCollector collector = createAndRegisterCollector(true);

        // Trigger initialization first by starting and ending a collection
        collector.startCollection().end();

        Tracer tracer = GlobalOpenTelemetry.getTracer("test");
        Span outsideSpan = tracer.spanBuilder("outsideSpan").startSpan();
        outsideSpan.end();

        SpanCollector.SpanHandle handle = collector.startCollection();

        Span insideSpan = tracer.spanBuilder("insideSpan").startSpan();
        insideSpan.end();

        List<SpanData> spans = handle.end();
        assertNotNull(spans);

        Set<String> spanNames = spans.stream().map(SpanData::getName).collect(Collectors.toSet());
        assertTrue(spanNames.contains("root"));
        assertTrue(spanNames.contains("insideSpan"));
        assertFalse(spanNames.contains("outsideSpan"));
    }

    @Test
    void testSpanDataSerialization() {
        SpanCollector collector = createAndRegisterCollector(true);

        SpanCollector.SpanHandle handle = collector.startCollection();
        Tracer tracer = GlobalOpenTelemetry.getTracer("test");

        Span span = tracer.spanBuilder("testSpan").startSpan();
        span.setAttribute("http.method", "GET");
        span.setAttribute("http.status_code", 200);
        span.end();

        List<SpanData> spans = handle.end();
        assertNotNull(spans);

        SpanData testSpan =
                spans.stream().filter(s -> s.getName().equals("testSpan")).findFirst().orElse(null);
        assertNotNull(testSpan);
        assertEquals(16, testSpan.getSpanId().length());
        assertEquals("INTERNAL", testSpan.getKind());
        assertTrue(testSpan.getStartTime() > 0);
        assertTrue(testSpan.getEndTime() > 0);
        assertTrue(testSpan.getEndTime() >= testSpan.getStartTime());

        assertNotNull(testSpan.getAttributes());
        assertEquals("GET", testSpan.getAttributes().get("http.method"));
        assertEquals(200L, testSpan.getAttributes().get("http.status_code"));
    }
}
