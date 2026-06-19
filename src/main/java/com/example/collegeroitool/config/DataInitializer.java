package com.example.collegeroitool.config;

import com.example.collegeroitool.model.*;
import com.example.collegeroitool.repository.*;
import com.example.collegeroitool.service.InstitutionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds required reference data on every startup. Idempotent — safe to run multiple times.
 *
 * Mirrors the V9 Flyway migration so local dev (H2, Flyway disabled) gets the same
 * demo students as Railway prod (PostgreSQL, Flyway enabled).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final String DEMO_EMAIL    = "demo@callistotech.org";
    private static final String DEMO_NAME     = "Callisto Demo";
    private static final String DEMO_PASSWORD = "CallistoDemo2025!";

    private final UserRepository             userRepository;
    private final PasswordEncoder            passwordEncoder;
    private final InstitutionRepository      institutionRepository;
    private final StudentRepository          studentRepository;
    private final StudentDocumentRepository  documentRepository;
    private final DocumentKvExtractRepository kvRepository;
    private final InstitutionService         institutionService;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           InstitutionRepository institutionRepository,
                           StudentRepository studentRepository,
                           StudentDocumentRepository documentRepository,
                           DocumentKvExtractRepository kvRepository,
                           @Lazy InstitutionService institutionService) {
        this.userRepository       = userRepository;
        this.passwordEncoder      = passwordEncoder;
        this.institutionRepository = institutionRepository;
        this.studentRepository    = studentRepository;
        this.documentRepository   = documentRepository;
        this.kvRepository         = kvRepository;
        this.institutionService   = institutionService;
    }

    @Override
    public void run(String... args) {
        // 1. Default institution
        if (institutionRepository.findByCode("callisto-tech").isEmpty()) {
            Institution inst = new Institution();
            inst.setName("Callisto Tech");
            inst.setCode("callisto-tech");
            institutionRepository.save(inst);
        }

        // 2. Demo counselor account
        AppUser demo = userRepository.findByEmail(DEMO_EMAIL).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(DEMO_EMAIL);
            u.setName(DEMO_NAME);
            u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            u.setProvider("local");
            u.setSubscriptionActive(true);
            return userRepository.save(u);
        });
        // V9 Flyway inserts the user with NULL password_hash — patch it here.
        if (demo.getPasswordHash() == null) {
            demo.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            userRepository.save(demo);
        }
        try { institutionService.ensureDefaultEnrollment(demo); } catch (Exception ignored) {}

        // 3. Demo students under the demo counselor (mirrors V9 migration for prod)
        seedDemoStudents(demo);

        // 4. Also seed demo students under the dev bypass user so local dev searches work.
        //    Ensure dev@local exists (normally created lazily on first request).
        AppUser devUser = userRepository.findByEmail("dev@local").orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail("dev@local");
            u.setName("Dev User");
            u.setProvider("local");
            u.setSubscriptionActive(true);
            return userRepository.save(u);
        });
        try { institutionService.ensureDefaultEnrollment(devUser); } catch (Exception ignored) {}
        seedDemoStudents(devUser);

        // 5. Back-fill any pre-existing students into the default institution.
        try { institutionService.backfillStudentsToDefaultInstitution(); } catch (Exception ignored) {}
    }

    private void seedDemoStudents(AppUser counselor) {
        List<DemoStudent> students = List.of(
            new DemoStudent("Emma", "Richardson", LocalDate.of(2006, 3, 14),
                "emma-richardson", "2023_1040.pdf",
                """
                {
                  "Tax Year": "2023",
                  "Filing Status": "Married Filing Jointly",
                  "Adjusted Gross Income": "52400",
                  "Total Wages and Salaries": "48200",
                  "Interest Income": "320",
                  "Federal Income Tax Withheld": "4100",
                  "Number of Dependents": "3",
                  "Child Tax Credit": "2000",
                  "Education Savings Account Balance": "8500",
                  "Enrollment Status": "Full-time",
                  "Major": "Nursing"
                }"""),

            new DemoStudent("Marcus", "Johnson", LocalDate.of(2002, 8, 22),
                "marcus-johnson", "2023_1040.pdf",
                """
                {
                  "Tax Year": "2023",
                  "Filing Status": "Single",
                  "Adjusted Gross Income": "18200",
                  "Total Wages and Salaries": "17800",
                  "Federal Income Tax Withheld": "890",
                  "Earned Income Credit": "540",
                  "Interest Income": "0",
                  "Enrollment Status": "Full-time",
                  "Major": "Computer Science",
                  "First Generation College Student": "Yes",
                  "Number of Dependents": "0"
                }"""),

            new DemoStudent("Sofia", "Ramirez", LocalDate.of(2007, 1, 9),
                "sofia-ramirez", "2023_1040.pdf",
                """
                {
                  "Tax Year": "2023",
                  "Filing Status": "Married Filing Jointly",
                  "Adjusted Gross Income": "145000",
                  "Total Wages and Salaries": "130000",
                  "Interest Income": "2100",
                  "Dividend Income": "4800",
                  "Capital Gains": "8200",
                  "Federal Income Tax Withheld": "24200",
                  "Investment Portfolio Value": "185000",
                  "529 Plan Balance": "42000",
                  "Number of Dependents": "2",
                  "Enrollment Status": "Full-time",
                  "Major": "Pre-Law"
                }"""),

            new DemoStudent("Aiden", "Park", LocalDate.of(2005, 11, 30),
                "aiden-park", "2023_1040.pdf",
                """
                {
                  "Tax Year": "2023",
                  "Filing Status": "Single",
                  "Adjusted Gross Income": "38500",
                  "Total Wages and Salaries": "36200",
                  "Federal Income Tax Withheld": "3100",
                  "Interest Income": "85",
                  "Child Support Received": "4800",
                  "Number of Dependents": "1",
                  "Housing Allowance": "12000",
                  "Enrollment Status": "Full-time",
                  "Major": "Business Administration",
                  "Special Circumstance": "Divorced parents — custodial parent only on FAFSA"
                }"""),

            new DemoStudent("Priya", "Sharma", LocalDate.of(2004, 6, 17),
                "priya-sharma", "2023_1040.pdf",
                """
                {
                  "Tax Year": "2023",
                  "Filing Status": "Married Filing Jointly",
                  "Adjusted Gross Income": "76300",
                  "Total Wages and Salaries": "71500",
                  "Federal Income Tax Withheld": "9800",
                  "Interest Income": "410",
                  "Business Income": "4800",
                  "Self Employment Tax": "680",
                  "Education Credits": "2500",
                  "529 Plan Balance": "15000",
                  "Number of Dependents": "2",
                  "Enrollment Status": "Full-time",
                  "Major": "Electrical Engineering",
                  "First Generation College Student": "Yes"
                }""")
        );

        for (DemoStudent ds : students) {
            // Idempotent: skip if this student already exists for the demo counselor
            boolean exists = studentRepository
                .findByUserIdAndFirstNameIgnoreCaseAndLastNameIgnoreCaseAndDateOfBirth(
                    counselor.getId(), ds.firstName, ds.lastName, ds.dob)
                .isPresent();
            if (exists) continue;

            Student student = new Student();
            student.setUserId(counselor.getId());
            student.setFirstName(ds.firstName);
            student.setLastName(ds.lastName);
            student.setDateOfBirth(ds.dob);
            student = studentRepository.save(student);

            try { institutionService.enrollStudentInCounselorInstitution(student, counselor.getId()); }
            catch (Exception ignored) {}

            StudentDocument doc = new StudentDocument();
            doc.setStudentId(student.getId());
            doc.setBlobName("demo/" + ds.slug + "-1040-2023.pdf");
            doc.setBlobUrl("/api/demo/document/" + ds.slug + "-1040-2023.pdf");
            doc.setOriginalFilename(ds.originalFilename);
            doc = documentRepository.save(doc);

            DocumentKvExtract kv = new DocumentKvExtract();
            kv.setDocumentId(doc.getId());
            kv.setKvJson(ds.kvJson.strip());
            kvRepository.save(kv);
        }
    }

    private record DemoStudent(
        String firstName, String lastName, LocalDate dob,
        String slug, String originalFilename, String kvJson) {}
}
