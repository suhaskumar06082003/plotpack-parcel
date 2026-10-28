package com.plotpack.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "SMS sent to customers (e-token, delivery confirmation)")
public class NotificationController {
    private final NotificationRepository repository;
    private final LiveHub hub;

    public NotificationController(NotificationRepository repository, LiveHub hub) {
        this.repository = repository;
        this.hub = hub;
    }

    @GetMapping
    @Operation(summary = "Recent notifications, optionally for one phone number")
    public List<Notification> list(@RequestParam(required = false) String phone,
                                   @RequestParam(defaultValue = "30") int limit) {
        var page = PageRequest.of(0, Math.min(limit, 200));
        return phone == null || phone.isBlank()
                ? repository.findAllByOrderByIdDesc(page)
                : repository.findByPhoneOrderByIdDesc(phone, page);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live stream of notifications (Server-Sent Events)")
    public SseEmitter stream() {
        return hub.subscribe();
    }
}
