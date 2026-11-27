package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.exception.ApiException;
import com.grantinofarms.poultry.repository.AuditRepository;
import com.grantinofarms.poultry.repository.ReportSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportAttachmentService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_FILES_PER_VERSION = 10;
    private final ReportSubmissionRepository submissions;
    private final AuditRepository audit;

    public ReportAttachmentService(ReportSubmissionRepository submissions, AuditRepository audit) {
        this.submissions = submissions;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> upload(String submissionId, MultipartFile file) {
        RoleAuthorization.requireReportSubmitter();
        Map<String, Object> header = requireAccess(submissionId);
        String actor = CurrentUserContext.get();
        if (!actor.equals(header.get("authorId"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ATTACHMENT_AUTHOR_ONLY",
                    "Only the report author can attach evidence to a draft.");
        }
        String status = String.valueOf(header.get("lifecycleStatus"));
        if (!List.of("DRAFT", "CHANGES_REQUESTED").contains(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_VERSION_LOCKED",
                    "Evidence can only be attached before submission or while a report is returned for changes.");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_SIZE",
                    "Evidence must be a non-empty file no larger than 5 MB.");
        }
        int version = ((Number) header.get("currentVersion")).intValue();
        if (submissions.findAttachments(submissionId, version).size() >= MAX_FILES_PER_VERSION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_LIMIT_REACHED",
                    "A report version can contain at most 10 evidence files.");
        }
        String mime = file.getContentType();
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_READ_FAILED", "The evidence file could not be read.");
        }
        validateContent(mime, bytes);
        String fileName = safeFileName(file.getOriginalFilename());
        String sha256 = HexFormat.of().formatHex(digest(bytes));
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        submissions.insertAttachment(id, submissionId, version, fileName, mime,
                bytes.length, sha256, bytes, actor, now);
        submissions.appendEvent(submissionId, version, actor, "EVIDENCE_ATTACHED",
                "{\"attachmentId\":\"" + id + "\",\"fileName\":\"" + escape(fileName)
                        + "\",\"sha256\":\"" + sha256 + "\"}", now);
        audit.append(String.valueOf(header.get("farmId")), "ATTACH_EVIDENCE", "REPORT_SUBMISSION",
                submissionId, "Evidence attached to report draft", null,
                "{\"attachmentId\":\"" + id + "\",\"version\":" + version + "}", now);
        return submissions.findAttachments(submissionId, version).stream()
                .filter(row -> id.equals(row.get("id"))).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String submissionId) {
        Map<String, Object> header = requireAccess(submissionId);
        int version = header.get("submittedVersion") instanceof Number n
                ? n.intValue() : ((Number) header.get("currentVersion")).intValue();
        return submissions.findAttachments(submissionId, version);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> download(String submissionId, String attachmentId) {
        requireAccess(submissionId);
        Map<String, Object> attachment = submissions.findAttachment(submissionId, attachmentId);
        if (attachment == null) throw new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND",
                "Evidence attachment was not found.");
        return attachment;
    }

    private Map<String, Object> requireAccess(String id) {
        RoleAuthorization.requireReportSubmitter();
        Map<String, Object> header = submissions.findHeader(id);
        if (header == null) throw new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                "Report submission was not found.");
        if (!"MANAGER".equals(CurrentUserContext.getRole())
                && !CurrentUserContext.get().equals(header.get("authorId"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                    "Report submission was not found.");
        }
        return header;
    }

    private void validateContent(String mime, byte[] bytes) {
        boolean valid = switch (mime == null ? "" : mime) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "application/pdf" -> bytes.length >= 5
                    && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            case "text/plain" -> isPlainText(bytes);
            default -> false;
        };
        if (!valid) throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_ATTACHMENT",
                "Only valid JPEG, PNG, PDF, or UTF-8 text files are accepted.");
    }

    private boolean isPlainText(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (Exception exception) { return false; }
    }

    private String safeFileName(String original) {
        String name = original == null ? "evidence" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) name = "evidence";
        return name.length() > 160 ? name.substring(name.length() - 160) : name;
    }

    private byte[] digest(byte[] bytes) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable.", exception); }
    }

    private String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
