package com.devflow.kit;

import com.devflow.kit.KitDtos.KitDetail;
import com.devflow.kit.KitDtos.KitRequest;
import com.devflow.kit.KitDtos.KitSummary;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kits")
public class KitController {

    private final KitService service;

    public KitController(KitService service) {
        this.service = service;
    }

    @GetMapping
    public List<KitSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public KitDetail get(@PathVariable long id) {
        return service.get(id);
    }

    /** Also used to import a kit previously downloaded from /export. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KitDetail create(@Valid @RequestBody KitRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public KitDetail update(@PathVariable long id, @Valid @RequestBody KitRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public KitDetail duplicate(@PathVariable long id) {
        return service.duplicate(id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<KitRequest> export(@PathVariable long id) {
        KitRequest kit = service.export(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(kit.slug() + ".devflow-kit.json").build().toString())
                .body(kit);
    }
}
