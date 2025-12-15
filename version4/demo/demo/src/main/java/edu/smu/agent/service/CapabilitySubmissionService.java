package edu.smu.agent.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Service
public class CapabilitySubmissionService {

    private static final Logger log = LoggerFactory.getLogger(CapabilitySubmissionService.class);
    private static final Path STORAGE_FILE = Path.of("data", "capability-submissions.json");

    private final Map<String, Submission> submissions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public CapabilitySubmissionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadFromDisk() {
        try {
            if (STORAGE_FILE.getParent() != null) {
                Files.createDirectories(STORAGE_FILE.getParent());
            }

            if (!Files.exists(STORAGE_FILE)) {
                return;
            }

            List<Submission> stored = objectMapper.readValue(
                    STORAGE_FILE.toFile(),
                    new TypeReference<List<Submission>>() {
                    });

            stored.forEach(item -> {
                if (item.getId() == null) {
                    item.setId(UUID.randomUUID().toString());
                }
                if (item.getCreatedAt() == null) {
                    item.setCreatedAt(Instant.now());
                }
                if (item.getDecisionReason() == null && item.getDecisionRemark() != null) {
                    item.setDecisionReason(item.getDecisionRemark());
                }
                submissions.put(item.getId(), item);
            });
            log.info("Loaded {} capability submissions from disk", stored.size());
        } catch (Exception e) {
            log.warn("Failed to load submissions from disk", e);
        }
    }

    public Submission createSubmission(Map<String, Object> payload, String submittedBy, String submittedByUsername) {
        Submission submission = new Submission();
        submission.setId(UUID.randomUUID().toString());
        submission.setCompanyName(asText(payload.get("companyName")));
        submission.setCreditCode(asText(payload.get("creditCode")));
        submission.setCompanyScale(asText(payload.get("companyScale")));
        submission.setCompanyType(asText(payload.get("companyType")));
        submission.setCompanyAddress(asText(payload.get("companyAddress")));
        submission.setBusinessIntro(asText(payload.get("businessIntro")));
        submission.setCoreProducts(asList(payload.get("coreProducts")));
        submission.setIntellectualProperties(asList(payload.get("intellectualProperties")));
        submission.setPatents(asList(payload.get("patents")));
        submission.setContactName(asText(payload.get("contactName")));
        submission.setContactInfo(asText(payload.get("contactInfo")));
        submission.setSubmittedBy(submittedBy);
        submission.setSubmittedByUsername(submittedByUsername);
        submission.setStatus(Status.PENDING);
        submission.setCreatedAt(Instant.now());

        submissions.put(submission.getId(), submission);
        persistSafely();
        return submission;
    }

    public List<Submission> listSubmissions() {
        return submissions.values()
                .stream()
                .sorted(Comparator.comparing(Submission::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    public Submission getSubmission(String id) {
        return submissions.get(id);
    }

    public List<Submission> listSubmissionsByUser(String username) {
        if (username == null) {
            return List.of();
        }

        return submissions.values()
                .stream()
                .filter(item -> username.equals(item.getSubmittedByUsername()))
                .sorted(Comparator.comparing(Submission::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
    }

    public Submission decide(String id, Status status, String remark, String decisionBy, String decisionByName) {
        Submission submission = submissions.get(id);
        if (submission == null) {
            return null;
        }

        submission.setStatus(status);
        submission.setDecisionRemark(remark);
        submission.setDecisionReason(remark);
        submission.setDecisionBy(decisionBy);
        submission.setDecisionByName(decisionByName);
        submission.setDecisionAt(Instant.now());
        persistSafely();
        return submission;
    }

    public Map<String, Object> buildWorkflowInputs(Submission submission) {
        if (submission == null) {
            return Map.of();
        }

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("companyName", submission.getCompanyName());
        inputs.put("creditCode", submission.getCreditCode());
        inputs.put("companyScale", submission.getCompanyScale());
        inputs.put("companyType", submission.getCompanyType());
        inputs.put("companyAddress", submission.getCompanyAddress());
        inputs.put("businessIntro", submission.getBusinessIntro());
        inputs.put("coreProducts", joinList(submission.getCoreProducts()));
        inputs.put("intellectualProperties", joinList(submission.getIntellectualProperties()));
        inputs.put("patents", joinList(submission.getPatents()));
        inputs.put("contactName", submission.getContactName());
        inputs.put("contactInfo", submission.getContactInfo());
        inputs.put("submittedBy", submission.getSubmittedBy());
        inputs.put("submittedByUsername", submission.getSubmittedByUsername());
        return inputs;
    }

    public Submission recordWorkflowResult(String id, Map<String, Object> workflowResult) {
        Submission submission = submissions.get(id);
        if (submission == null || workflowResult == null) {
            return submission;
        }

        submission.setWorkflowRunId(asText(workflowResult.get("workflowRunId")));
        submission.setWorkflowTaskId(asText(workflowResult.get("taskId")));
        submission.setWorkflowResponse(workflowResult);
        persistSafely();
        return submission;
    }

    private synchronized void persistSafely() {
        try {
            if (STORAGE_FILE.getParent() != null) {
                Files.createDirectories(STORAGE_FILE.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(STORAGE_FILE.toFile(), new ArrayList<>(submissions.values()));
        } catch (Exception e) {
            log.warn("Failed to persist submissions", e);
        }
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<String> asList(Object value) {
        if (value instanceof List<?>) {
            return ((List<Object>) value).stream().map(this::asText).toList();
        }

        if (value == null) {
            return List.of();
        }

        return List.of(asText(value));
    }

    private String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        return String.join("、", values);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Submission {
        private String id;
        private String companyName;
        private String creditCode;
        private String companyScale;
        private String companyType;
        private String companyAddress;
        private String businessIntro;
        private List<String> coreProducts;
        private List<String> intellectualProperties;
        private List<String> patents;
        private String contactName;
        private String contactInfo;
        private String submittedBy;
        private String submittedByUsername;
        private Status status;
        private Instant createdAt;
        private Instant decisionAt;
        private String decisionRemark;
        private String decisionBy;
        private String decisionByName;
        private String decisionReason;
        private String workflowRunId;
        private String workflowTaskId;
        private Map<String, Object> workflowResponse;
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }
}