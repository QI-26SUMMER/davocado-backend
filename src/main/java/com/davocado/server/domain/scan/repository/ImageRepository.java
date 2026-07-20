package com.davocado.server.domain.scan.repository;

import com.davocado.server.domain.scan.entity.Image;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {

    Optional<Image> findByScanId(Long scanId);

    List<Image> findByScanIdIn(Collection<Long> scanIds);
}
