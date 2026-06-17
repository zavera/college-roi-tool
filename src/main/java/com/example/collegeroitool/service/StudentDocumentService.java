package com.example.collegeroitool.service;

import com.example.collegeroitool.model.DocumentKvExtract;
import com.example.collegeroitool.model.Student;
import com.example.collegeroitool.model.StudentDocument;
import com.example.collegeroitool.repository.DocumentKvExtractRepository;
import com.example.collegeroitool.repository.StudentDocumentRepository;
import com.example.collegeroitool.repository.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class StudentDocumentService {

    private final StudentRepository studentRepository;
    private final StudentDocumentRepository documentRepository;
    private final DocumentKvExtractRepository kvExtractRepository;
    private final AzureBlobStorageService blobStorageService;
    private final AzureDocumentIntelligenceService documentIntelligenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StudentDocumentService(StudentRepository studentRepository,
                                  StudentDocumentRepository documentRepository,
                                  DocumentKvExtractRepository kvExtractRepository,
                                  AzureBlobStorageService blobStorageService,
                                  AzureDocumentIntelligenceService documentIntelligenceService) {
        this.studentRepository = studentRepository;
        this.documentRepository = documentRepository;
        this.kvExtractRepository = kvExtractRepository;
        this.blobStorageService = blobStorageService;
        this.documentIntelligenceService = documentIntelligenceService;
    }

    /**
     * Looks up a student by identity without creating one.
     * Returns student + docs if found, or an empty Optional.
     */
    public Optional<Map<String, Object>> searchStudent(Long userId, String firstName, String lastName,
                                                        LocalDate dateOfBirth) throws Exception {
        Optional<Student> found = studentRepository
            .findByUserIdAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirth(
                userId, firstName, lastName, dateOfBirth);
        if (found.isEmpty()) return Optional.empty();
        Student student = found.get();
        List<Map<String, Object>> docDtos = buildDocDtos(student.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", student.getId());
        result.put("firstName", student.getFirstName());
        result.put("middleName", student.getMiddleName());
        result.put("lastName", student.getLastName());
        result.put("dateOfBirth", student.getDateOfBirth().toString());
        result.put("documents", docDtos);
        return Optional.of(result);
    }

    /**
     * Finds an existing student by identity, or creates one if not found.
     * Then loads all active documents. Any with an empty or missing KV extract
     * will have re-extraction attempted immediately.
     * Returns a map shaped for the API response.
     */
    public Map<String, Object> findOrCreateStudent(Long userId,
                                                    String firstName,
                                                    String middleName,
                                                    String lastName,
                                                    LocalDate dateOfBirth) throws Exception {
        Student student = studentRepository
            .findByUserIdAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirth(
                userId, firstName, lastName, dateOfBirth)
            .orElseGet(() -> {
                Student s = new Student();
                s.setUserId(userId);
                s.setFirstName(firstName);
                s.setMiddleName(middleName);
                s.setLastName(lastName);
                s.setDateOfBirth(dateOfBirth);
                return studentRepository.save(s);
            });

        List<Map<String, Object>> docDtos = buildDocDtos(student.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", student.getId());
        result.put("firstName", student.getFirstName());
        result.put("middleName", student.getMiddleName());
        result.put("lastName", student.getLastName());
        result.put("dateOfBirth", student.getDateOfBirth().toString());
        result.put("documents", docDtos);
        return result;
    }

    /**
     * Uploads a document for a student:
     * 1. Stores bytes in Azure Blob (if blob storage is configured).
     * 2. Creates a student_documents record.
     * 3. Runs KV extraction on the in-memory bytes.
     * 4. Persists the KV extract.
     * Returns the document metadata + extracted KV.
     */
    public Map<String, Object> uploadDocument(Long studentId, MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        String blobName = "students/" + studentId + "/" + System.currentTimeMillis() + "_" + filename;
        String blobUrl;

        if (blobStorageService.isEnabled()) {
            blobUrl = blobStorageService.upload(blobName, file.getBytes(), file.getContentType());
        } else {
            // Blob storage not configured -- store a placeholder URL.
            // Documents can still be extracted at upload time from in-memory bytes.
            blobUrl = "blob-not-configured/" + blobName;
        }

        StudentDocument doc = new StudentDocument();
        doc.setStudentId(studentId);
        doc.setBlobName(blobName);
        doc.setBlobUrl(blobUrl);
        doc.setOriginalFilename(filename);
        // uploadedAt defaults to NOW() in the entity constructor
        doc.setActive(true);
        doc = documentRepository.save(doc);

        Map<String, String> extracted = documentIntelligenceService.extractKeyValuePairs(file);

        DocumentKvExtract kv = new DocumentKvExtract();
        kv.setDocumentId(doc.getId());
        kv.setKvJson(objectMapper.writeValueAsString(extracted));
        kv.setExtractedAt(LocalDateTime.now());
        kvExtractRepository.save(kv);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", doc.getId());
        result.put("filename", filename);
        result.put("blobUrl", blobUrl);
        result.put("uploadedAt", doc.getUploadedAt().toString());
        result.put("extracted", extracted);
        return result;
    }

    /**
     * Returns the merged KV map from all active documents for a student.
     * Used by downstream LLM prompts -- only active documents with non-empty KV are included.
     */
    public Map<String, String> getActiveKvPayload(Long studentId) {
        Map<String, String> merged = new LinkedHashMap<>();
        List<StudentDocument> activeDocs = documentRepository.findByStudentIdAndActiveTrue(studentId);
        for (StudentDocument doc : activeDocs) {
            kvExtractRepository.findByDocumentId(doc.getId()).ifPresent(kv -> {
                if (!isEmptyKv(kv.getKvJson())) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> pairs = objectMapper.readValue(kv.getKvJson(), Map.class);
                        merged.putAll(pairs);
                    } catch (Exception ignored) {}
                }
            });
        }
        return merged;
    }

    private List<Map<String, Object>> buildDocDtos(Long studentId) {
        List<StudentDocument> activeDocs = documentRepository.findByStudentIdAndActiveTrue(studentId);
        List<Map<String, Object>> dtos = new ArrayList<>();
        for (StudentDocument doc : activeDocs) {
            Optional<DocumentKvExtract> kvOpt = kvExtractRepository.findByDocumentId(doc.getId());

            // Attempt re-extraction if KV is absent or empty and blob storage is available
            if (blobStorageService.isEnabled() && (kvOpt.isEmpty() || isEmptyKv(kvOpt.map(DocumentKvExtract::getKvJson).orElse("{}")))) {
                kvOpt = attemptReExtraction(doc, kvOpt);
            }

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("documentId", doc.getId());
            dto.put("filename", doc.getOriginalFilename());
            dto.put("uploadedAt", doc.getUploadedAt().toString());
            dto.put("active", doc.isActive());
            dto.put("extracted", kvOpt.map(kv -> {
                try { return objectMapper.readValue(kv.getKvJson(), Map.class); }
                catch (Exception e) { return Map.of(); }
            }).orElse(Map.of()));
            dtos.add(dto);
        }
        return dtos;
    }

    private Optional<DocumentKvExtract> attemptReExtraction(StudentDocument doc,
                                                             Optional<DocumentKvExtract> existing) {
        try {
            String sasUrl = blobStorageService.generateSasUrl(doc.getBlobName(), Duration.ofMinutes(10));
            Map<String, String> extracted = documentIntelligenceService.extractKeyValuePairsFromUrl(sasUrl);
            if (extracted.isEmpty()) return existing;

            DocumentKvExtract kv = existing.orElseGet(() -> {
                DocumentKvExtract e = new DocumentKvExtract();
                e.setDocumentId(doc.getId());
                return e;
            });
            kv.setKvJson(objectMapper.writeValueAsString(extracted));
            kv.setExtractedAt(LocalDateTime.now());
            return Optional.of(kvExtractRepository.save(kv));
        } catch (Exception e) {
            return existing;
        }
    }

    private boolean isEmptyKv(String kvJson) {
        if (kvJson == null) return true;
        String trimmed = kvJson.trim();
        return trimmed.isEmpty() || "{}".equals(trimmed) || "[]".equals(trimmed);
    }
}
