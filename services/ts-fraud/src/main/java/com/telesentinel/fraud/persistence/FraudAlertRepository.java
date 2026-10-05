package com.telesentinel.fraud.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudAlertRepository extends JpaRepository<FraudAlertEntity, UUID> {
    List<FraudAlertEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<FraudAlertEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    List<FraudAlertEntity> findBySubscriberOrderByCreatedAtDesc(String subscriber);
}
