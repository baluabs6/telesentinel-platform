package com.telesentinel.ingestion.controller;

import com.telesentinel.ingestion.model.AlarmEvent;
import com.telesentinel.ingestion.model.CdrEvent;
import com.telesentinel.ingestion.service.IngestionService;
import com.telesentinel.ingestion.service.IngestionService.BatchResult;
import com.telesentinel.ingestion.service.IngestionService.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1")
public class IngestionController {

    private final IngestionService service;

    public IngestionController(IngestionService service) {
        this.service = service;
    }

    @PostMapping("/alarms")
    public ResponseEntity<Map<String, String>> alarm(@Valid @RequestBody AlarmEvent alarm) {
        return respond(service.ingestAlarm(alarm), alarm.alarmId());
    }

    @PostMapping("/cdrs")
    public ResponseEntity<Map<String, String>> cdr(@Valid @RequestBody CdrEvent cdr) {
        return respond(service.ingestCdr(cdr), cdr.cdrId());
    }

    /** Up to 1000 CDRs per call. Safe to retry whole: already-accepted CDRs are reported as duplicates. */
    @PostMapping("/cdrs/batch")
    public ResponseEntity<Map<String, Integer>> cdrBatch(
            @RequestBody @Size(min = 1, max = 1000) List<@Valid CdrEvent> cdrs) {
        BatchResult result = service.ingestCdrs(cdrs);
        return ResponseEntity.accepted()
                .body(Map.of("accepted", result.accepted(), "duplicates", result.duplicates()));
    }

    private ResponseEntity<Map<String, String>> respond(Result result, String id) {
        return result == Result.ACCEPTED
                ? ResponseEntity.accepted().body(Map.of("id", id, "status", "ACCEPTED"))
                : ResponseEntity.ok(Map.of("id", id, "status", "DUPLICATE_IGNORED"));
    }
}
