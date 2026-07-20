package com.davocado.server.domain.notification.repository;

import com.davocado.server.domain.notification.entity.Notification;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByScanIdIn(Collection<Long> scanIds);

    List<Notification> findByScanId(Long scanId);

    long countByUserIdAndStatus(Long userId, String status);
}
