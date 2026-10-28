package com.plotpack.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Pushes every sent notification to open browsers (Server-Sent Events), so the UI "phone" lights up live. */
@Component
public class LiveHub {
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper;

    public LiveHub(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        send(emitter, "hello", Map.of("ok", true));
        return emitter;
    }

    public void broadcast(Notification n) {
        emitters.forEach(e -> send(e, "notification", n));
    }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.forEach(e -> send(e, "ping", Map.of()));
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(mapper.writeValueAsString(data)));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }
}
