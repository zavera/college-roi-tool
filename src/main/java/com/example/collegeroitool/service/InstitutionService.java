package com.example.collegeroitool.service;

import com.example.collegeroitool.model.*;
import com.example.collegeroitool.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InstitutionService {

    private static final String DEFAULT_CODE = "callisto-tech";

    private static final List<String> PII_FRAGMENTS = List.of(
        "name", "ssn", "social", "dob", "birth", "address", "street", "zip",
        "phone", "email", "ein", "tin", "passport", "license", "account",
        "routing", "signature");
    private static final List<String> ALLOWED_FRAGMENTS = List.of(
        "gpa", "income", "agi", "wage", "grant", "aid", "major", "degree",
        "school", "credit", "tuition", "enrollment", "field", "program",
        "efc", "sai", "tax", "year", "balance");

    private final InstitutionRepository institutionRepository;
    private final UserInstitutionRepository userInstitutionRepository;
    private final StudentInstitutionRepository studentInstitutionRepository;
    private final StudentRepository studentRepository;
    private final StudentDocumentRepository documentRepository;
    private final DocumentKvExtractRepository kvExtractRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InstitutionService(InstitutionRepository institutionRepository,
                               UserInstitutionRepository userInstitutionRepository,
                               StudentInstitutionRepository studentInstitutionRepository,
                               StudentRepository studentRepository,
                               StudentDocumentRepository documentRepository,
                               DocumentKvExtractRepository kvExtractRepository) {
        this.institutionRepository = institutionRepository;
        this.userInstitutionRepository = userInstitutionRepository;
        this.studentInstitutionRepository = studentInstitutionRepository;
        this.studentRepository = studentRepository;
        this.documentRepository = documentRepository;
        this.kvExtractRepository = kvExtractRepository;
    }

    /** Returns the seeded default institution (Callisto Tech). */
    public Institution getDefaultInstitution() {
        return institutionRepository.findByCode(DEFAULT_CODE)
            .orElseThrow(() -> new IllegalStateException("Default institution not seeded — run V8 migration"));
    }

    /**
     * Ensures the user belongs to the default institution.
     * Idempotent — safe to call on every login.
     */
    public void ensureDefaultEnrollment(AppUser user) {
        Institution inst = getDefaultInstitution();
        UserInstitutionId key = new UserInstitutionId(user.getId(), inst.getId());
        if (!userInstitutionRepository.existsById(key)) {
            userInstitutionRepository.save(new UserInstitution(key));
        }
    }

    /**
     * Returns the user's first active institution.
     * Falls back to the default institution for demo use.
     */
    public Optional<Institution> resolveActiveInstitution(AppUser user) {
        return userInstitutionRepository.findByIdUserId(user.getId()).stream()
            .filter(UserInstitution::isActive)
            .map(ui -> institutionRepository.findById(ui.getId().getInstitutionId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * Back-fills all students that have no institution membership into the default institution.
     * Called from DataInitializer so pre-existing local students are visible in institutional chat.
     */
    public void backfillStudentsToDefaultInstitution() {
        Institution inst = getDefaultInstitution();
        studentRepository.findAll().forEach(student -> {
            StudentInstitutionId key = new StudentInstitutionId(student.getId(), inst.getId());
            if (!studentInstitutionRepository.existsById(key)) {
                studentInstitutionRepository.save(new StudentInstitution(key));
            }
        });
    }

    /**
     * Enrolls a newly-created student in the institution of their creating counselor.
     * Called from StudentDocumentService after student is persisted.
     */
    public void enrollStudentInCounselorInstitution(Student student, Long counselorUserId) {
        userInstitutionRepository.findByIdUserId(counselorUserId).stream()
            .filter(UserInstitution::isActive)
            .findFirst()
            .ifPresent(ui -> {
                StudentInstitutionId key =
                    new StudentInstitutionId(student.getId(), ui.getId().getInstitutionId());
                if (!studentInstitutionRepository.existsById(key)) {
                    studentInstitutionRepository.save(new StudentInstitution(key));
                }
            });
    }

    /**
     * Returns a FERPA-safe summary of all active students in the institution,
     * suitable for injection into the institutional chat prompt.
     * Student names ARE included (counselors query by name).
     * Financial KV is filtered: PII fields stripped, only financial/academic fields kept.
     */
    public List<Map<String, Object>> buildInstitutionalStudentContext(Long institutionId) {
        List<StudentInstitution> memberships =
            studentInstitutionRepository.findByIdInstitutionId(institutionId);

        List<Map<String, Object>> roster = new ArrayList<>();
        for (StudentInstitution si : memberships) {
            if (!si.isActive()) continue;
            studentRepository.findById(si.getId().getStudentId()).ifPresent(student -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", student.getFirstName() + " " + student.getLastName());
                entry.put("financialData", buildSafeKvSummary(student.getId()));
                roster.add(entry);
            });
        }
        return roster;
    }

    private String buildSafeKvSummary(Long studentId) {
        List<StudentDocument> docs = documentRepository.findByStudentIdAndActiveTrue(studentId);
        if (docs.isEmpty()) return "(no documents uploaded)";

        StringBuilder sb = new StringBuilder();
        for (StudentDocument doc : docs) {
            kvExtractRepository.findByDocumentId(doc.getId()).ifPresent(kv -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.readValue(kv.getKvJson(), Map.class);
                    String safe = map.entrySet().stream()
                        .filter(e -> {
                            String k = e.getKey().toLowerCase();
                            boolean hasPii = PII_FRAGMENTS.stream().anyMatch(k::contains);
                            if (hasPii) return false;
                            return ALLOWED_FRAGMENTS.stream().anyMatch(k::contains);
                        })
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining(", "));
                    if (!safe.isBlank()) sb.append(safe).append("; ");
                } catch (Exception ignored) {}
            });
        }
        String result = sb.toString().trim();
        return result.isBlank() ? "(no financial data extracted)" : result;
    }
}
