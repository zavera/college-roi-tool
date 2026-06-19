package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.Student;
import com.example.collegeroitool.model.StudentDocument;
import com.example.collegeroitool.repository.DocumentKvExtractRepository;
import com.example.collegeroitool.repository.StudentDocumentRepository;
import com.example.collegeroitool.repository.StudentRepository;
import com.example.collegeroitool.repository.UserRepository;
import com.example.collegeroitool.service.DemoPdfGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.*;

/**
 * Serves demo student documents as dynamically generated PDFs.
 * Replaces Azure Blob Storage in demo/prod mode — no file storage required.
 * Endpoint: GET /api/demo/document/{filename}
 * Filename pattern: {first}-{last}-1040-2023.pdf  e.g. emma-richardson-1040-2023.pdf
 */
@RestController
@RequestMapping("/api/demo/document")
public class DemoDocumentController {

    private static final String DEMO_EMAIL = "demo@callistotech.org";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final StudentDocumentRepository documentRepository;
    private final DocumentKvExtractRepository kvExtractRepository;
    private final DemoPdfGenerator pdfGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${demo.mode:false}")
    private boolean demoMode;

    public DemoDocumentController(UserRepository userRepository,
                                   StudentRepository studentRepository,
                                   StudentDocumentRepository documentRepository,
                                   DocumentKvExtractRepository kvExtractRepository,
                                   DemoPdfGenerator pdfGenerator) {
        this.userRepository    = userRepository;
        this.studentRepository = studentRepository;
        this.documentRepository = documentRepository;
        this.kvExtractRepository = kvExtractRepository;
        this.pdfGenerator      = pdfGenerator;
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<byte[]> serveDocument(@PathVariable String filename) {
        // 1. Serve real filled IRS form from classpath (fastest path)
        try {
            ClassPathResource resource = new ClassPathResource("demo-forms/" + filename);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    byte[] pdf = is.readAllBytes();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDisposition(
                        ContentDisposition.attachment().filename(filename).build());
                    return ResponseEntity.ok().headers(headers).body(pdf);
                }
            }
        } catch (Exception ignored) {}

        // 2. Fall back to dynamically generated PDF from DB KV data
        String[] nameParts = parseNameFromFilename(filename);
        if (nameParts == null) return ResponseEntity.notFound().build();
        String first = nameParts[0];
        String last  = nameParts[1];
        String displayName = capitalize(first) + " " + capitalize(last);

        Map<String, Object> kvData = loadKvDataByName(first, last);
        if (kvData.isEmpty()) {
            kvData = DEMO_KV_DATA.getOrDefault(first + "-" + last, Map.of());
        }
        if (kvData.isEmpty()) return ResponseEntity.notFound().build();

        try {
            byte[] pdf = pdfGenerator.generate(displayName, filename, kvData);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String[] parseNameFromFilename(String filename) {
        String base = filename.replaceAll("(?i)-1040.*\\.pdf$", "");
        String[] parts = base.split("-", 2);
        return parts.length < 2 ? null : parts;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private Map<String, Object> loadKvDataByName(String first, String last) {
        return studentRepository.findAll().stream()
            .filter(s -> s.getFirstName().equalsIgnoreCase(first)
                      && s.getLastName().equalsIgnoreCase(last))
            .findFirst()
            .map(student -> {
                List<StudentDocument> docs =
                    documentRepository.findByStudentIdAndActiveTrue(student.getId());
                if (docs.isEmpty()) return Map.<String, Object>of();
                return kvExtractRepository.findByDocumentId(docs.get(0).getId())
                    .map(kv -> {
                        try {
                            return objectMapper.<Map<String, Object>>readValue(
                                kv.getKvJson(), new TypeReference<>() {});
                        } catch (Exception e) { return Map.<String, Object>of(); }
                    }).orElse(Map.of());
            }).orElse(Map.of());
    }

    // Hardcoded fallback — mirrors V9 seed data exactly so the endpoint works
    // before DB is seeded and in local dev (where Flyway is disabled).
    private static final Map<String, Map<String, Object>> DEMO_KV_DATA = Map.of(
        "emma-richardson", new LinkedHashMap<>(Map.of(
            "Tax Year", "2023", "Filing Status", "Married Filing Jointly",
            "Adjusted Gross Income", "52,400", "Total Wages and Salaries", "48,200",
            "Federal Income Tax Withheld", "4,100", "Interest Income", "320",
            "Number of Dependents", "3", "Child Tax Credit", "2,000",
            "Education Savings Account Balance", "8,500", "Major", "Nursing")),
        "marcus-johnson", new LinkedHashMap<>(Map.of(
            "Tax Year", "2023", "Filing Status", "Single",
            "Adjusted Gross Income", "18,200", "Total Wages and Salaries", "17,800",
            "Federal Income Tax Withheld", "890", "Earned Income Credit", "540",
            "Interest Income", "0", "Enrollment Status", "Full-time",
            "Major", "Computer Science", "First Generation College Student", "Yes")),
        "sofia-ramirez", new LinkedHashMap<>(Map.of(
            "Tax Year", "2023", "Filing Status", "Married Filing Jointly",
            "Adjusted Gross Income", "145,000", "Total Wages and Salaries", "130,000",
            "Interest Income", "2,100", "Dividend Income", "4,800",
            "Capital Gains", "8,200", "Federal Income Tax Withheld", "24,200",
            "Investment Portfolio Value", "185,000", "529 Plan Balance", "42,000")),
        "aiden-park", new LinkedHashMap<>(Map.of(
            "Tax Year", "2023", "Filing Status", "Single",
            "Adjusted Gross Income", "38,500", "Total Wages and Salaries", "36,200",
            "Federal Income Tax Withheld", "3,100", "Child Support Received", "4,800",
            "Number of Dependents", "1", "Major", "Business Administration",
            "Special Circumstance", "Divorced — custodial parent only on FAFSA")),
        "priya-sharma", new LinkedHashMap<>(Map.of(
            "Tax Year", "2023", "Filing Status", "Married Filing Jointly",
            "Adjusted Gross Income", "76,300", "Total Wages and Salaries", "71,500",
            "Federal Income Tax Withheld", "9,800", "Business Income", "4,800",
            "Education Credits", "2,500", "529 Plan Balance", "15,000",
            "Major", "Electrical Engineering", "First Generation College Student", "Yes"))
    );
}
