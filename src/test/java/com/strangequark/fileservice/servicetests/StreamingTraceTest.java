package com.strangequark.fileservice.servicetests;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureObservability
public class StreamingTraceTest extends BaseServiceTest {
    @Autowired
    private Tracer tracer;

    @Test
    void fileAndZipStreamsKeepRequestTraceOnAnotherThreadTest() throws Exception {
        for(boolean zip : new boolean[]{false, true}) {
            Span span = tracer.nextSpan().name("download-test").start();
            StreamingResponseBody stream;
            try(Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                stream = zip ? fileService.downloadAllFiles(collectionName).getBody()
                        : fileService.downloadFile(collectionName, fileName).getBody();
            }
            assertNotNull(stream);
            AtomicReference<String> observedTrace = new AtomicReference<>();
            ByteArrayOutputStream output = new ByteArrayOutputStream() {
                @Override
                public synchronized void write(byte[] bytes, int offset, int length) {
                    observedTrace.set(MDC.get("traceId"));
                    super.write(bytes, offset, length);
                }
            };

            try(var worker = Executors.newSingleThreadExecutor()) {
                worker.submit(() -> {
                    stream.writeTo(output);
                    assertNull(tracer.currentSpan());
                    assertNull(MDC.get("traceId"));
                    return null;
                }).get(10, TimeUnit.SECONDS);
                assertEquals(span.context().traceId(), observedTrace.get());
                assertTrue(output.size() > 0);
            } finally {
                span.end();
            }
        }
    }
}
