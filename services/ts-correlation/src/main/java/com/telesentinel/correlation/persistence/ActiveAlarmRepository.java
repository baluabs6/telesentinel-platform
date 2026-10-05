package com.telesentinel.correlation.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ActiveAlarmRepository extends JpaRepository<ActiveAlarmEntity, String> {

    @Transactional
    @Modifying
    @Query("delete from ActiveAlarmEntity a where a.lastSeen < :cutoff")
    int purgeOlderThan(@Param("cutoff") Instant cutoff);
}
