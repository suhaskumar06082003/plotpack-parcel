package com.plotpack.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByEventId(String eventId);

    List<Notification> findAllByOrderByIdDesc(Pageable pageable);

    List<Notification> findByPhoneOrderByIdDesc(String phone, Pageable pageable);
}
