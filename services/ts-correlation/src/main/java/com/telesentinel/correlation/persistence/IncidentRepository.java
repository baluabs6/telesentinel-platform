package com.telesentinel.correlation.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {
    Optional<IncidentEntity> findFirstByRootNodeAndStatus(String rootNode, String status);
    List<IncidentEntity> findByStatus(String status);
    List<IncidentEntity> findAllByOrderByOpenedAtDesc(Pageable pageable);
}
