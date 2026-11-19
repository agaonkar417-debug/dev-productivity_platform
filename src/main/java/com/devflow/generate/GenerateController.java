package com.devflow.generate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/kits/{id}")
public class GenerateController {

    /** Request body: {"variables": {"entity": "order item", "basePackage": "com.acme.shop"}} */
    public record GenerateRequest(Map<String, String> variables) {
    }

    private final GenerationService generation;

    public GenerateController(GenerationService generation) {
        this.generation = generation;
    }

    @PostMapping("/preview")
    public GenerationService.Result preview(@PathVariable long id, @RequestBody GenerateRequest request) {
        return generation.preview(id, request.variables());
    }

    @PostMapping(value = "/generate", produces = "application/zip")
    public ResponseEntity<byte[]> generate(@PathVariable long id, @RequestBody GenerateRequest request) {
        byte[] zip = generation.generateZip(id, request.variables());
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("devflow-kit-" + id + "-" + stamp + ".zip")
                                .build().toString())
                .body(zip);
    }
}
