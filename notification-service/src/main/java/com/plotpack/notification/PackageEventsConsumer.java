package com.plotpack.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plotpack.common.events.EventTypes;
import com.plotpack.common.events.PlotPackEvent;
import com.plotpack.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Kafka consumer: package-service events -> SMS to the customer.
 * <ul>
 *   <li>PACKAGE_ALLOCATED -> "your e-token is PP-XXXXXX, item is in bin A-1-02"</li>
 *   <li>ORDER_DELIVERED   -> "delivered successfully, thank you for shopping"</li>
 * </ul>
 * Kafka is at-least-once, so the same event can arrive twice; the UNIQUE event_id makes the second a no-op.
 */
@Component
public class PackageEventsConsumer {
    private static final Logger log = LoggerFactory.getLogger(PackageEventsConsumer.class);
    private static final NumberFormat RUPEES = NumberFormat.getNumberInstance(new Locale("en", "IN"));

    private final ObjectMapper mapper;
    private final NotificationRepository repository;
    private final LiveHub hub;

    public PackageEventsConsumer(ObjectMapper mapper, NotificationRepository repository, LiveHub hub) {
        this.mapper = mapper;
        this.repository = repository;
        this.hub = hub;
    }

    @KafkaListener(topics = Topics.PACKAGE_EVENTS, groupId = "notification-service")
    public void on(String json) throws Exception {
        PlotPackEvent e = mapper.readValue(json, PlotPackEvent.class);
        if (repository.existsByEventId(e.eventId())) {
            log.info("Duplicate event {} ignored", e.eventId());
            return;
        }
        Notification n = switch (e.eventType()) {
            case EventTypes.PACKAGE_ALLOCATED -> new Notification(e.eventId(), Notification.Kind.E_TOKEN, e.str("token"),
                    e.str("customerName"), e.str("customerPhone"),
                    "Hi %s, your %s (Rs.%s) from floor %s is ready at the ground floor. E-token: %s. Estimated bill: Rs.%s for %s item(s). Show this token at the pickup counter."
                            .formatted(firstName(e.str("customerName")), e.str("description"), rupees(e.str("amount")),
                                    e.str("floor"), e.str("token"), rupees(e.str("estimatedBill")), e.str("tokenPackageCount")));
            case EventTypes.ORDER_DELIVERED -> new Notification(e.eventId(), Notification.Kind.DELIVERED, e.str("token"),
                    e.str("customerName"), e.str("customerPhone"),
                    "Hi %s, your %s package(s) under e-token %s were delivered successfully. Thank you for shopping at Jayalakshmi Silks!"
                            .formatted(firstName(e.str("customerName")), e.str("packageCount"), e.str("token")));
            default -> null;
        };
        if (n == null) return;
        try {
            repository.save(n);
        } catch (DataIntegrityViolationException duplicate) {
            return; // a parallel redelivery won the race
        }
        // A real system would call an SMS gateway here; we push it to the UI's "customer phone" instead.
        log.info("SMS to {}: {}", n.getPhone(), n.getMessage());
        hub.broadcast(n);
    }

    private static String firstName(String name) {
        return name == null ? "Customer" : name.split(" ")[0];
    }

    private static String rupees(String amount) {
        return amount == null ? "0" : RUPEES.format(new BigDecimal(amount));
    }
}
