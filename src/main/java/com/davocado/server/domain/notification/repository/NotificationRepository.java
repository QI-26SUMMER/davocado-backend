package com.davocado.server.domain.notification.repository;

import com.davocado.server.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByScanIdIn(Collection<Long> scanIds);

    List<Notification> findByScanId(Long scanId);

    long countByUserIdAndStatus(Long userId, String status);

    List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);

    List<Notification> findByUserIdAndStatusOrderByIdDesc(Long userId, String status, Pageable pageable);

    List<Notification> findByUserIdAndStatusAndIdLessThanOrderByIdDesc(
            Long userId, String status, Long id, Pageable pageable);

    List<Notification> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            String status, Instant scheduledAt, Pageable pageable);
}
