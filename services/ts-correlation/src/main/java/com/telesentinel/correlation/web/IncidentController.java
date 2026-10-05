package com.telesentinel.correlation.web;

import com.telesentinel.correlation.persistence.IncidentEntity;
import com.telesentinel.correlation.persistence.IncidentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentRepository repository;

    public IncidentController(IncidentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<IncidentEntity> latest(@RequestParam(defaultValue = "20") int limit) {
        return repository.findAllByOrderByOpenedAtDesc(PageRequest.of(0, Math.min(limit, 200)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentEntity> get(@PathVariable UUID id) {
        return repository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<IncidentEntity> resolve(@PathVariable UUID id) {
        return repository.findById(id).map(i -> {
            i.close("RESOLVED");
            return ResponseEntity.ok(repository.save(i));
        }).orElse(ResponseEntity.notFound().build());
    }
}
