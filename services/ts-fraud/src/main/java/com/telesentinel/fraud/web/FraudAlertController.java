package com.telesentinel.fraud.web;

import com.telesentinel.fraud.persistence.FraudAlertEntity;
import com.telesentinel.fraud.persistence.FraudAlertRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudAlertController {

    private static final Set<String> STATUSES = Set.of("OPEN", "CONFIRMED", "FALSE_POSITIVE", "RESOLVED");

    public record StatusUpdate(String status) { }

    private final FraudAlertRepository repository;

    public FraudAlertController(FraudAlertRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/alerts")
    public List<FraudAlertEntity> latest(@RequestParam(defaultValue = "20") int limit,
                                         @RequestParam(required = false) String status) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        return status == null
                ? repository.findAllByOrderByCreatedAtDesc(page)
                : repository.findByStatusOrderByCreatedAtDesc(status, page);
    }

    @GetMapping("/alerts/subscriber/{msisdn}")
    public List<FraudAlertEntity> bySubscriber(@PathVariable String msisdn) {
        return repository.findBySubscriberOrderByCreatedAtDesc(msisdn);
    }

    /** Analyst triage: mark an alert CONFIRMED, FALSE_POSITIVE or RESOLVED. */
    @PatchMapping("/alerts/{id}/status")
    public ResponseEntity<Object> updateStatus(@PathVariable UUID id, @RequestBody StatusUpdate body) {
        if (body == null || body.status() == null || !STATUSES.contains(body.status())) {
            return ResponseEntity.badRequest().body(Map.of("error", "status must be one of " + STATUSES));
        }
        return repository.findById(id).map(alert -> {
            alert.updateStatus(body.status());
            return ResponseEntity.<Object>ok(repository.save(alert));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
