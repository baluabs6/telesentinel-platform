package com.telesentinel.ingestion.repository;

import com.telesentinel.ingestion.model.RawEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RawEventRepository extends MongoRepository<RawEvent, String> {
}
