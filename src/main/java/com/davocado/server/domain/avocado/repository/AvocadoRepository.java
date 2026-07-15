package com.davocado.server.domain.avocado.repository;

import com.davocado.server.domain.avocado.entity.Avocado;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvocadoRepository extends JpaRepository<Avocado, Long> {

    List<Avocado> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<Avocado> findByUserIdAndStatusOrderByIdDesc(Long userId, String status, Pageable pageable);

    List<Avocado> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);

    List<Avocado> findByUserIdAndStatusAndIdLessThanOrderByIdDesc(
            Long userId, String status, Long id, Pageable pageable);
}
