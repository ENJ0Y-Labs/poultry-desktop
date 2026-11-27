package com.grantinofarms.poultry.controller;

import com.grantinofarms.poultry.dto.ReportSubmissionRequest;
import com.grantinofarms.poultry.service.ReportSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/submissions")
public class ReportSubmissionController {
    private final ReportSubmissionService service;
    private final com.grantinofarms.poultry.service.ReportReferenceService references;
    private final com.grantinofarms.poultry.service.ReportAttachmentService attachments;

    public ReportSubmissionController(ReportSubmissionService service,
                                      com.grantinofarms.poultry.service.ReportReferenceService references,
                                      com.grantinofarms.poultry.service.ReportAttachmentService attachments) {
        this.service = service;
        this.references = references;
        this.attachments = attachments;
    }

    @GetMapping("/references")
    public Map<String, Object> references() {
        return Map.of("ok", true, "data", references.list());
    }

    @PostMapping(value = "/{id}/attachments", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAttachment(@PathVariable String id,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return Map.of("ok", true, "data", attachments.upload(id, file));
    }

    @GetMapping("/{id}/attachments")
    public Map<String, Object> attachments(@PathVariable String id) {
        return Map.of("ok", true, "data", attachments.list(id));
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public org.springframework.http.ResponseEntity<byte[]> downloadAttachment(
            @PathVariable String id, @PathVariable String attachmentId) {
        Map<String, Object> attachment = attachments.download(id, attachmentId);
        byte[] content = (byte[]) attachment.get("content");
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(String.valueOf(attachment.get("mimeType"))))
                .contentLength(content.length)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment()
                                .filename(String.valueOf(attachment.get("fileName")), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }

    @PostMapping
    public Map<String, Object> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReportSubmissionRequest request) {
        return Map.of("ok", true, "data", service.createDraft(request, idempotencyKey));
    }

    @PutMapping("/{id}")
    public Map<String, Object> save(@PathVariable String id,
                                    @Valid @RequestBody ReportSubmissionRequest request) {
        return Map.of("ok", true, "data", service.saveDraft(id, request));
    }

    @PostMapping("/{id}/submit")
    public Map<String, Object> submit(@PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String operationKey) {
        return Map.of("ok", true, "data", service.submit(id, operationKey));
    }

    @GetMapping
    public Map<String, Object> mine() {
        return Map.of("ok", true, "data", service.mine());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return Map.of("ok", true, "data", service.get(id));
    }
}
