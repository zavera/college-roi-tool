package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.FafsaProfile;
import com.example.collegeroitool.repository.FafsaProfileRepository;
import com.example.collegeroitool.service.AzureDocumentIntelligenceService;
import com.example.collegeroitool.service.DependencyStatusService;
import com.example.collegeroitool.service.ExtractionProgressRegistry;
import com.example.collegeroitool.service.FsaHandbookService;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.InstitutionService;
import com.example.collegeroitool.service.SaiCalculatorService;
import com.example.collegeroitool.service.StudentDocumentService;
import com.example.collegeroitool.service.TavilySearchClient;
import com.example.collegeroitool.service.UserService;
import com.example.collegeroitool.model.Institution;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fafsa")
public class FafsaPrepController {

    private static final int FREE_FAFSA_USES = 3;
    private static final List<String> CSS_SCHOOL_DOMAINS_EXCLUDED = TavilySearchClient.DEFAULT_EXCLUDED_DOMAINS;

    private final FafsaProfileRepository fafsaProfileRepository;
    private final UserService userService;
    private final AzureDocumentIntelligenceService azureDocumentIntelligenceService;
    private final GroqService groqService;
    private final TavilySearchClient tavilySearchClient;
    private final FsaHandbookService fsaHandbookService;
    private final DependencyStatusService dependencyStatusService;
    private final SaiCalculatorService saiCalculatorService;
    private final StudentDocumentService studentDocumentService;
    private final ExtractionProgressRegistry progressRegistry;
    private final InstitutionService institutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    @Value("${demo.mode:false}")
    private boolean demoMode;

    public FafsaPrepController(FafsaProfileRepository fafsaProfileRepository, UserService userService,
                                AzureDocumentIntelligenceService azureDocumentIntelligenceService,
                                GroqService groqService, TavilySearchClient tavilySearchClient,
                                FsaHandbookService fsaHandbookService,
                                DependencyStatusService dependencyStatusService,
                                SaiCalculatorService saiCalculatorService,
                                StudentDocumentService studentDocumentService,
                                ExtractionProgressRegistry progressRegistry,
                                InstitutionService institutionService) {
        this.fafsaProfileRepository = fafsaProfileRepository;
        this.userService = userService;
        this.azureDocumentIntelligenceService = azureDocumentIntelligenceService;
        this.groqService = groqService;
        this.tavilySearchClient = tavilySearchClient;
        this.fsaHandbookService = fsaHandbookService;
        this.dependencyStatusService = dependencyStatusService;
        this.saiCalculatorService = saiCalculatorService;
        this.studentDocumentService = studentDocumentService;
        this.progressRegistry = progressRegistry;
        this.institutionService = institutionService;
    }

    // ── STUDENT LOOKUP + DOCUMENT UPLOAD ────────────────────────────────────

    /**
     * Searches for a student by name+DOB without creating one.
     * Returns {found:true, ...studentData} or {found:false}.
     */
    @PostMapping("/student/search")
    public ResponseEntity<?> searchStudent(@RequestBody Map<String, String> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        try {
            String firstName = body.get("firstName");
            String lastName = body.get("lastName");
            String dob = body.get("dateOfBirth");
            if (firstName == null || lastName == null || dob == null)
                return ResponseEntity.badRequest().body(Map.of("error", "firstName, lastName, and dateOfBirth are required"));
            Optional<Map<String, Object>> result = studentDocumentService.searchStudent(
                user.getId(), firstName, lastName, java.time.LocalDate.parse(dob));
            if (result.isPresent()) {
                Map<String, Object> resp = new LinkedHashMap<>(result.get());
                resp.put("found", true);
                return ResponseEntity.ok(resp);
            }
            return ResponseEntity.ok(Map.of("found", false));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Finds or creates a student by name+DOB, returns the student record with all
     * active documents and their KV extracts. Documents with an empty KV extract
     * will have re-extraction attempted automatically.
     */
    @PostMapping("/student/find-or-create")
    public ResponseEntity<?> findOrCreateStudent(@RequestBody Map<String, String> body, Principal principal) {
        if (demoMode) return ResponseEntity.status(403).body(Map.of("error", "Student management is disabled in demo mode."));
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        try {
            String firstName = body.get("firstName");
            String middleName = body.getOrDefault("middleName", null);
            String lastName = body.get("lastName");
            String dob = body.get("dateOfBirth");
            if (firstName == null || lastName == null || dob == null)
                return ResponseEntity.badRequest().body(Map.of("error", "firstName, lastName, and dateOfBirth are required"));
            Map<String, Object> result = studentDocumentService.findOrCreateStudent(
                user.getId(), firstName, middleName, lastName, java.time.LocalDate.parse(dob));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Opens an SSE stream for real-time extraction progress.
     * The browser opens this before starting uploads; the upload endpoint pushes events here.
     */
    @GetMapping(value = "/student/{studentId}/extract-stream", produces = "text/event-stream")
    public SseEmitter extractionStream(@PathVariable Long studentId, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) {
            SseEmitter err = new SseEmitter(0L);
            err.completeWithError(new RuntimeException("Not authenticated"));
            return err;
        }
        return progressRegistry.create(studentId);
    }

    /**
     * Uploads a document for an existing student, stores the blob, extracts KV, and
     * persists a document_kv_extracts record. Emits SSE events at each stage.
     */
    @PostMapping("/student/{studentId}/upload")
    public ResponseEntity<?> uploadStudentDocument(@PathVariable Long studentId,
                                                    @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                    Principal principal) {
        if (demoMode) return ResponseEntity.status(403).body(Map.of("error", "Document upload is disabled in demo mode."));
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        String filename = file.getOriginalFilename();
        progressRegistry.send(studentId, Map.of("filename", filename, "status", "uploading"));
        try {
            Map<String, Object> result = studentDocumentService.uploadDocument(studentId, file,
                () -> progressRegistry.send(studentId, Map.of("filename", filename, "status", "extracting")));
            int kvCount = result.get("extracted") instanceof Map<?,?> m ? m.size() : 0;
            progressRegistry.send(studentId, Map.of("filename", filename, "status", "done", "kvCount", kvCount));
            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            progressRegistry.send(studentId, Map.of("filename", filename, "status", "error"));
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty()) return ResponseEntity.ok(Map.of("exists", false));
        return ResponseEntity.ok(toResponseMap(profileOpt.get(), true));
    }

    @PostMapping("/profile")
    public ResponseEntity<?> saveProfile(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        FafsaProfile profile = fafsaProfileRepository.findByUser_Email(user.getEmail()).orElseGet(() -> {
            FafsaProfile p = new FafsaProfile();
            p.setUser(user);
            return p;
        });

        profile.setStudentName((String) body.get("studentName"));
        Object dob = body.get("dateOfBirth");
        if (dob != null && !dob.toString().isBlank()) {
            try {
                profile.setDateOfBirth(LocalDate.parse(dob.toString()));
            } catch (Exception ignored) { /* leave unset if unparsable */ }
        }
        Object planningYear = body.get("planningYear");
        if (planningYear != null && !planningYear.toString().isBlank()) {
            try {
                profile.setPlanningYear(Integer.parseInt(planningYear.toString()));
            } catch (NumberFormatException ignored) { /* leave unset */ }
        }
        profile.setUpdatedAt(LocalDateTime.now());
        fafsaProfileRepository.save(profile);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    @PostMapping("/upload-tax-doc")
    public ResponseEntity<?> uploadTaxDoc(@RequestParam("file") MultipartFile file, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Save your demographics first."));
        }
        FafsaProfile profile = profileOpt.get();

        try {
            Map<String, String> extracted = azureDocumentIntelligenceService.extractKeyValuePairs(file);

            Map<String, Object> merged = profile.getExtractedDataJson() != null
                ? objectMapper.readValue(profile.getExtractedDataJson(), Map.class)
                : new LinkedHashMap<>();
            merged.put(file.getOriginalFilename(), extracted);
            profile.setExtractedDataJson(objectMapper.writeValueAsString(merged));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(Map.of("filename", file.getOriginalFilename(), "extracted", extracted));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not extract document data: " + e.getMessage()));
        }
    }

    @PostMapping("/readiness-summary")
    public ResponseEntity<?> generateReadinessSummary(Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Save your demographics and upload a tax document first."));
        }
        FafsaProfile profile = profileOpt.get();

        try {
            String aiJson = groqService.getFafsaReadinessSummary(profile);
            Map<String, Object> summary = parseJsonOrWrap(aiJson, "readinessSummary");

            @SuppressWarnings("unchecked")
            List<String> scholarshipQueries = (List<String>) summary.getOrDefault("scholarshipQueries", List.of());
            List<Map<String, Object>> liveScholarships = new ArrayList<>();
            for (String query : scholarshipQueries) {
                liveScholarships.addAll(tavilySearchClient.search(query, 4, TavilySearchClient.DEFAULT_EXCLUDED_DOMAINS));
            }
            summary.put("liveScholarships", liveScholarships);

            String summaryJson = objectMapper.writeValueAsString(summary);
            profile.setReadinessSummaryJson(summaryJson);
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate readiness summary: " + e.getMessage()));
        }
    }

    @PostMapping("/roadmap")
    public ResponseEntity<?> generateRoadmap(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty() || profileOpt.get().getReadinessSummaryJson() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Generate your readiness summary first."));
        }
        FafsaProfile profile = profileOpt.get();

        try {
            Object selectedOptions = body.getOrDefault("selectedOptions", List.of());
            String selectedOptionsJson = objectMapper.writeValueAsString(selectedOptions);
            profile.setSelectedOptionsJson(selectedOptionsJson);

            Map<String, Object> existingSummary = objectMapper.readValue(profile.getReadinessSummaryJson(), Map.class);
            String deadlinesJson = objectMapper.writeValueAsString(existingSummary.getOrDefault("deadlines", List.of()));

            String aiJson = groqService.getFafsaRoadmap(profile, deadlinesJson, selectedOptionsJson);
            Map<String, Object> roadmap = parseJsonOrWrap(aiJson, "summary");

            profile.setRoadmapJson(objectMapper.writeValueAsString(roadmap));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(roadmap);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate roadmap: " + e.getMessage()));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        String question = (String) body.get("message");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required."));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());
        String historyText = history.stream()
            .map(m -> m.getOrDefault("role", "user") + ": " + m.getOrDefault("content", ""))
            .collect(Collectors.joining("\n"));

        // Resolve institution from server-side session — never trust a client-supplied institutionId.
        Institution institution = institutionService.resolveActiveInstitution(user)
            .orElseGet(institutionService::getDefaultInstitution);

        List<Map<String, Object>> roster =
            institutionService.buildInstitutionalStudentContext(institution.getId());

        try {
            String answer = groqService.getInstitutionalChatResponse(
                institution.getName(), roster, historyText, question);
            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(Map.of("answer", answer, "institutionName", institution.getName()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not get a response: " + e.getMessage()));
        }
    }

    // ── Dependency Status Calculator (deterministic, ungated) ──────────────────

    @GetMapping("/dependency-status/questions")
    public ResponseEntity<?> getDependencyQuestions() {
        return ResponseEntity.ok(Map.of("questions", dependencyStatusService.getQuestions()));
    }

    @PostMapping("/dependency-status")
    public ResponseEntity<?> evaluateDependencyStatus(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        FafsaProfile profile = findOrCreateProfile(user);

        @SuppressWarnings("unchecked")
        Map<String, Object> rawAnswers = (Map<String, Object>) body.getOrDefault("answers", Map.of());
        Map<String, Boolean> answers = new LinkedHashMap<>();
        rawAnswers.forEach((k, v) -> answers.put(k, Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v))));

        try {
            Map<String, Object> result = dependencyStatusService.evaluate(answers);
            profile.setDependencyStatus((String) result.get("status"));
            profile.setDependencyAnswersJson(objectMapper.writeValueAsString(answers));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not evaluate dependency status: " + e.getMessage()));
        }
    }

    // ── Target Schools (ungated) ────────────────────────────────────────────────

    @PostMapping("/target-schools")
    public ResponseEntity<?> saveTargetSchools(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        FafsaProfile profile = findOrCreateProfile(user);
        try {
            Object schools = body.getOrDefault("schools", List.of());
            profile.setTargetSchoolsJson(objectMapper.writeValueAsString(schools));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);
            return ResponseEntity.ok(Map.of("saved", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not save target schools: " + e.getMessage()));
        }
    }

    // ── Multi-year SAI Projector (calc ungated; AI commentary gated) ───────────

    @PostMapping("/sai-projection")
    public ResponseEntity<?> projectSai(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        FafsaProfile profile = findOrCreateProfile(user);
        String dependencyStatus = profile.getDependencyStatus() != null ? profile.getDependencyStatus() : "dependent";

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawYears = (List<Map<String, Object>>) body.getOrDefault("years", List.of());
            List<SaiCalculatorService.YearInput> years = rawYears.stream().map(y -> new SaiCalculatorService.YearInput(
                intOf(y.get("year"), LocalDate.now().getYear()),
                doubleOf(y.get("parentAgi")), doubleOf(y.get("parentUntaxedIncome")), doubleOf(y.get("parentTaxesPaid")), doubleOf(y.get("parentAssets")),
                doubleOf(y.get("studentAgi")), doubleOf(y.get("studentUntaxedIncome")), doubleOf(y.get("studentAssets")),
                intOf(y.get("familySize"), 1)
            )).toList();

            List<Map<String, Object>> projection = saiCalculatorService.projectMultiYear(years, dependencyStatus);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dependencyStatusAssumed", profile.getDependencyStatus() == null);
            result.put("projection", projection);

            boolean wantsCommentary = Boolean.TRUE.equals(body.get("includeCommentary"));
            if (wantsCommentary) {
                if (!isUsageAllowed(user)) {
                    result.put("commentary", null);
                    result.put("paywalled", true);
                } else {
                    String projectionJson = objectMapper.writeValueAsString(projection);
                    String aiJson = groqService.getSaiStrategyCommentary(projectionJson);
                    result.put("commentary", parseJsonOrWrap(aiJson, "summary"));
                    userService.incrementFafsaUsageCount(user.getEmail());
                }
            }

            profile.setSaiProjectionJson(objectMapper.writeValueAsString(result));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not project SAI: " + e.getMessage()));
        }
    }

    // ── Asset Repositioning Advisor (AI, gated) ─────────────────────────────────

    @PostMapping("/asset-repositioning")
    public ResponseEntity<?> getAssetRepositioning(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        try {
            // Use KV from request body (already extracted by Azure DI) — no profile required
            String kvJson = "{}";
            Object extractedData = body.get("extractedData");
            Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
            if (extractedData != null) {
                kvJson = objectMapper.writeValueAsString(extractedData);
            } else {
                // Fall back to profile-stored KV if no data sent in request
                if (profileOpt.isPresent()) {
                    String fromDocs = buildKvJsonFromStudentDocs(user, profileOpt.get());
                    if (fromDocs != null) kvJson = fromDocs;
                    else if (profileOpt.get().getExtractedDataJson() != null) kvJson = profileOpt.get().getExtractedDataJson();
                }
            }

            Integer planningYear = profileOpt.map(FafsaProfile::getPlanningYear).orElse(null);
            String awardYear = fsaHandbookService.resolveAwardYear(planningYear);
            String handbookContent = fsaHandbookService.fetchAssetRepositioningContent(awardYear);

            // Tax year consistency check
            Integer expectedTaxYear = planningYear != null ? fsaHandbookService.expectedTaxYear(planningYear) : null;
            Integer extractedTaxYear = fsaHandbookService.detectTaxYearFromKv(kvJson);
            String taxYearNote;
            if (expectedTaxYear == null) {
                taxYearNote = "College start year not set — cannot verify tax year alignment.";
            } else if (extractedTaxYear == null) {
                taxYearNote = "Could not detect tax year from uploaded documents. Expected: " + expectedTaxYear + ".";
            } else if (!extractedTaxYear.equals(expectedTaxYear)) {
                taxYearNote = "MISMATCH: Uploaded documents appear to be from " + extractedTaxYear
                    + " but the " + awardYear + " FAFSA requires " + expectedTaxYear
                    + " tax data. Flag this clearly in the discrepancy field.";
            } else {
                taxYearNote = "OK: Uploaded documents (" + extractedTaxYear + ") match the expected tax year for the " + awardYear + " FAFSA.";
            }

            String dependencyStatus = profileOpt.map(FafsaProfile::getDependencyStatus).orElse(null);
            String aiJson = groqService.getAssetRepositioningAdvice(kvJson, awardYear, handbookContent,
                expectedTaxYear, extractedTaxYear, taxYearNote, dependencyStatus);
            Map<String, Object> advice = parseJsonOrWrap(aiJson, "generalNote");

            // Persist result on profile if one exists
            fafsaProfileRepository.findByUser_Email(user.getEmail()).ifPresent(p -> {
                try {
                    p.setAssetRepositioningJson(objectMapper.writeValueAsString(advice));
                    p.setUpdatedAt(LocalDateTime.now());
                    fafsaProfileRepository.save(p);
                } catch (Exception ignored) {}
            });

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(advice);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate asset repositioning advice: " + e.getMessage()));
        }
    }

    // ── Professional Judgment Screener (AI, gated) ──────────────────────────────

    @PostMapping("/professional-judgment")
    public ResponseEntity<?> getProfessionalJudgmentAppeal(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        FafsaProfile profile = findOrCreateProfile(user);

        try {
            Object circumstances = body.getOrDefault("circumstances", List.of());
            String circumstancesJson = objectMapper.writeValueAsString(circumstances);

            Object extractedData = body.getOrDefault("extractedData", Map.of());
            String extractedDataJson = objectMapper.writeValueAsString(extractedData);

            String awardYear = fsaHandbookService.resolveAwardYear(profile.getPlanningYear());
            String handbookContent = fsaHandbookService.fetchProfessionalJudgmentContent(awardYear);

            String letter = groqService.getProfessionalJudgmentAppeal(profile.getStudentName(), circumstancesJson, extractedDataJson, awardYear, handbookContent);
            String letterText = letter;
            String checklistText = "";
            int splitIdx = letter.indexOf("---CHECKLIST---");
            if (splitIdx >= 0) {
                letterText = letter.substring(0, splitIdx).trim();
                checklistText = letter.substring(splitIdx + 15).trim();
            }

            profile.setPjScreenerJson(circumstancesJson);
            profile.setPjAppealLetter(letterText);
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(Map.of("letter", letterText, "checklist", checklistText));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate appeal letter: " + e.getMessage()));
        }
    }

    // ── CSS vs. FAFSA Differential Explainer (AI + live search, gated) ─────────

    @PostMapping("/css-explainer")
    public ResponseEntity<?> getCssExplainer(Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty() || profileOpt.get().getTargetSchoolsJson() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Add your target schools first."));
        }
        FafsaProfile profile = profileOpt.get();

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> schools = objectMapper.readValue(profile.getTargetSchoolsJson(), List.class);

            List<Map<String, Object>> flaggedSchools = new ArrayList<>();
            for (Map<String, Object> school : schools) {
                String name = String.valueOf(school.get("name"));
                List<Map<String, Object>> results = tavilySearchClient.search(
                    "does " + name + " require CSS Profile financial aid", 1, CSS_SCHOOL_DOMAINS_EXCLUDED);
                Map<String, Object> flagged = new LinkedHashMap<>(school);
                flagged.put("cssSearchResult", results.isEmpty() ? null : results.get(0));
                flaggedSchools.add(flagged);
            }
            String targetSchoolsResultJson = objectMapper.writeValueAsString(flaggedSchools);

            String aiJson = groqService.getCssFafsaExplainer(profile.getExtractedDataJson(), targetSchoolsResultJson);
            Map<String, Object> explainer = parseJsonOrWrap(aiJson, "overallNote");
            explainer.put("schools", flaggedSchools);

            profile.setCssExplainerJson(objectMapper.writeValueAsString(explainer));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(explainer);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate CSS explainer: " + e.getMessage()));
        }
    }

    // ── Filing Date Optimizer (live search, gated) ──────────────────────────────

    @PostMapping("/filing-deadlines")
    public ResponseEntity<?> getFilingDeadlines(Principal principal) {
        AppUser user = resolveCurrentUser(principal).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        if (!isUsageAllowed(user)) return ResponseEntity.ok(Map.of("paywalled", true));

        Optional<FafsaProfile> profileOpt = fafsaProfileRepository.findByUser_Email(user.getEmail());
        if (profileOpt.isEmpty() || profileOpt.get().getTargetSchoolsJson() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Add your target schools first."));
        }
        FafsaProfile profile = profileOpt.get();

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> schools = objectMapper.readValue(profile.getTargetSchoolsJson(), List.class);
            int year = profile.getPlanningYear() != null ? profile.getPlanningYear() : LocalDate.now().getYear();

            List<Map<String, Object>> schoolDeadlines = new ArrayList<>();
            for (Map<String, Object> school : schools) {
                String name = String.valueOf(school.get("name"));
                List<Map<String, Object>> results = tavilySearchClient.search(
                    name + " financial aid deadline priority FAFSA CSS Profile " + year, 3, CSS_SCHOOL_DOMAINS_EXCLUDED);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("school", name);
                entry.put("results", results);
                schoolDeadlines.add(entry);
            }

            Map<String, Object> federalDeadline = Map.of(
                "name", "Federal FAFSA filing window",
                "timing", "Opens October 1 the year before enrollment; federal deadline is June 30 of the award year",
                "note", "File as early as possible — many state and institutional deadlines are much earlier and first-come, first-served."
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("federalDeadline", federalDeadline);
            result.put("schoolDeadlines", schoolDeadlines);

            profile.setFilingDeadlinesJson(objectMapper.writeValueAsString(result));
            profile.setUpdatedAt(LocalDateTime.now());
            fafsaProfileRepository.save(profile);

            userService.incrementFafsaUsageCount(user.getEmail());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not fetch filing deadlines: " + e.getMessage()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FafsaProfile findOrCreateProfile(AppUser user) {
        return fafsaProfileRepository.findByUser_Email(user.getEmail()).orElseGet(() -> {
            FafsaProfile p = new FafsaProfile();
            p.setUser(user);
            return p;
        });
    }

    private static int intOf(Object v, int fallback) {
        if (v == null) return fallback;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return fallback; }
    }

    private static double doubleOf(Object v) {
        if (v == null) return 0;
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * Looks up the student record linked to this profile's name+DOB, then returns their
     * merged document KV pairs as a JSON string. Returns null if no student is found or KV is empty.
     */
    private String buildKvJsonFromStudentDocs(AppUser user, FafsaProfile profile) {
        if (profile.getStudentName() == null || profile.getDateOfBirth() == null) return null;
        try {
            String[] parts = profile.getStudentName().trim().split("\\s+");
            String firstName = parts[0];
            String lastName = parts[parts.length - 1]; // skip middle name(s)
            Optional<Map<String, Object>> found = studentDocumentService.searchStudent(
                user.getId(), firstName, lastName, profile.getDateOfBirth());
            if (found.isEmpty()) return null;
            Long studentId = Long.valueOf(found.get().get("studentId").toString());
            Map<String, String> kv = studentDocumentService.getActiveKvPayload(studentId);
            if (kv.isEmpty()) return null;
            return objectMapper.writeValueAsString(kv);
        } catch (Exception e) {
            return null;
        }
    }

    /** Resolves the current user strictly from the authenticated session — never from request params/body. */
    private Optional<AppUser> resolveCurrentUser(Principal principal) {
        if (principal == null) {
            return devBypass ? Optional.of(userService.findOrCreateDevUser()) : Optional.empty();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth.getPrincipal() instanceof OAuth2User oAuth2User)
            ? oAuth2User.<String>getAttribute("email")
            : principal.getName();
        return email != null ? userService.findByEmail(email) : Optional.empty();
    }

    private boolean isUsageAllowed(AppUser user) {
        return user.isSubscriptionActive() || user.getFafsaUsageCount() < FREE_FAFSA_USES;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonOrWrap(String aiJson, String fallbackKey) {
        try {
            return objectMapper.readValue(aiJson, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>(Map.of(fallbackKey, aiJson));
        }
    }

    private Map<String, Object> toResponseMap(FafsaProfile profile, boolean exists) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("exists", exists);
        map.put("studentName", profile.getStudentName());
        map.put("dateOfBirth", profile.getDateOfBirth());
        map.put("planningYear", profile.getPlanningYear());
        map.put("extractedData", parseOrNull(profile.getExtractedDataJson()));
        map.put("readinessSummary", parseOrNull(profile.getReadinessSummaryJson()));
        map.put("selectedOptions", parseOrNull(profile.getSelectedOptionsJson()));
        map.put("roadmap", parseOrNull(profile.getRoadmapJson()));
        map.put("dependencyStatus", profile.getDependencyStatus());
        map.put("dependencyAnswers", parseOrNull(profile.getDependencyAnswersJson()));
        map.put("assetRepositioning", parseOrNull(profile.getAssetRepositioningJson()));
        map.put("pjScreener", parseOrNull(profile.getPjScreenerJson()));
        map.put("pjAppealLetter", profile.getPjAppealLetter());
        map.put("targetSchools", parseOrNull(profile.getTargetSchoolsJson()));
        map.put("cssExplainer", parseOrNull(profile.getCssExplainerJson()));
        map.put("filingDeadlines", parseOrNull(profile.getFilingDeadlinesJson()));
        map.put("saiProjection", parseOrNull(profile.getSaiProjectionJson()));
        return map;
    }

    private Object parseOrNull(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
