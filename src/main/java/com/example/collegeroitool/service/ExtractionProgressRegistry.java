package com.example.collegeroitool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one SseEmitter per active upload session (keyed by studentId).
 * The upload endpoint calls send() at each pipeline stage so the browser
 * can display per-file status in real time.
 */
@Component
public class ExtractionProgressRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExtractionProgressRegistry.class);
    private static final long EMITTER_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Called when the browser opens the SSE stream before uploading. */
    public SseEmitter create(Long studentId) {
        // Clean up any stale emitter for this student
        SseEmitter old = emitters.remove(studentId);
        if (old != null) { try { old.complete(); } catch (Exception ignored) {} }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.put(studentId, emitter);
        emitter.onCompletion(() -> emitters.remove(studentId, emitter));
        emitter.onTimeout(() -> { emitters.remove(studentId, emitter); emitter.complete(); });
        emitter.onError(e -> emitters.remove(studentId, emitter));
        return emitter;
    }

    /**
     * Sends a status event to the browser.
     * data must be JSON-serialisable — typically Map.of("filename", ..., "status", ...).
     */
    public void send(Long studentId, Map<String, Object> data) {
        SseEmitter emitter = emitters.get(studentId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                .name("status")
                .data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.debug("SSE send failed for student {}: {}", studentId, e.getMessage());
            emitters.remove(studentId, emitter);
        }
    }
}
