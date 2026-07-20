package com.davocado.server.domain.scan.repository;

import com.davocado.server.domain.scan.entity.Scan;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRepository extends JpaRepository<Scan, Long> {

    List<Scan> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<Scan> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);

    long countByUserId(Long userId);
}
