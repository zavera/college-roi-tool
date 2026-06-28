package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.FafsaPrepEntry;
import com.example.collegeroitool.repository.FafsaPrepRepository;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/fafsa-prep")
public class FafsaPrepController {

    private static final Logger log = LoggerFactory.getLogger(FafsaPrepController.class);

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    private final FafsaPrepRepository repo;
    private final UserService userService;
    private final GroqService groqService;

    public FafsaPrepController(FafsaPrepRepository repo,
                               UserService userService,
                               GroqService groqService) {
        this.repo        = repo;
        this.userService = userService;
        this.groqService = groqService;
    }

    /** List all FAFSA prep entries for the current user, newest first. */
    @GetMapping
    public ResponseEntity<?> list(Principal principal) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (FafsaPrepEntry e : repo.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            rows.add(toMap(e));
        }
        return ResponseEntity.ok(rows);
    }

    /** Save a new FAFSA prep entry and run the Groq analysis. */
    @PostMapping
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        FafsaPrepEntry e = new FafsaPrepEntry();
        e.setUser(user);
        e.setAddedBy(str(body, "addedBy", user.getName() != null ? user.getName() : user.getEmail()));
        e.setLabel(str(body, "label", null));
        e.setPlanningYear(intVal(body, "planningYear"));
        e.setDependencyStatus(str(body, "dependencyStatus", "dependent"));

        // Student income
        e.setStudentAgi(decimal(body, "studentAgi"));
        e.setStudentTaxesPaid(decimal(body, "studentTaxesPaid"));
        e.setStudentUntaxedIncome(decimal(body, "studentUntaxedIncome"));
        e.setStudentWorkStudy(decimal(body, "studentWorkStudy"));

        // Student assets
        e.setStudentCashSavings(decimal(body, "studentCashSavings"));
        e.setStudentInvestments(decimal(body, "studentInvestments"));
        e.setStudentBusinessNetWorth(decimal(body, "studentBusinessNetWorth"));

        // Household
        e.setHouseholdSize(intVal(body, "householdSize"));
        e.setNumberInCollege(intVal(body, "numberInCollege"));

        // Parent income
        e.setParentAgi(decimal(body, "parentAgi"));
        e.setParentTaxesPaid(decimal(body, "parentTaxesPaid"));
        e.setParentUntaxedIncome(decimal(body, "parentUntaxedIncome"));
        e.setParentMaritalStatus(str(body, "parentMaritalStatus", null));
        e.setParentAge(intVal(body, "parentAge"));

        // Parent assets
        e.setParentCashSavings(decimal(body, "parentCashSavings"));
        e.setParentInvestments(decimal(body, "parentInvestments"));
        e.setParentHomeEquity(decimal(body, "parentHomeEquity"));
        e.setParentRetirementSavings(decimal(body, "parentRetirementSavings"));
        e.setParentBusinessNetWorth(decimal(body, "parentBusinessNetWorth"));
        e.setParent529Balance(decimal(body, "parent529Balance"));

        // Persist first so we have an id
        FafsaPrepEntry saved = repo.save(e);

        // Run Groq analysis
        try {
            String analysis = groqService.getFafsaPrepAnalysis(saved);
            saved.setAssetRepositioningJson(analysis);
            saved = repo.save(saved);
        } catch (Exception ex) {
            log.warn("[fafsa-prep] Groq analysis failed id={}: {}", saved.getId(), ex.getMessage());
        }

        return ResponseEntity.ok(toMap(saved));
    }

    /** Delete a specific entry (only the owner can delete). */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Principal principal) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Optional<FafsaPrepEntry> entry = repo.findByIdAndUserId(id, user.getId());
        if (entry.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        repo.delete(entry.get());
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private AppUser resolveUser(Principal principal) {
        if (principal == null) return devBypass ? userService.findOrCreateDevUser() : null;
        return userService.findByEmail(principal.getName()).orElse(null);
    }

    private Map<String, Object> toMap(FafsaPrepEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                       e.getId());
        m.put("addedBy",                  e.getAddedBy());
        m.put("label",                    e.getLabel());
        m.put("planningYear",             e.getPlanningYear());
        m.put("dependencyStatus",         e.getDependencyStatus());
        m.put("studentAgi",               e.getStudentAgi());
        m.put("studentTaxesPaid",         e.getStudentTaxesPaid());
        m.put("studentUntaxedIncome",     e.getStudentUntaxedIncome());
        m.put("studentWorkStudy",         e.getStudentWorkStudy());
        m.put("studentCashSavings",       e.getStudentCashSavings());
        m.put("studentInvestments",       e.getStudentInvestments());
        m.put("studentBusinessNetWorth",  e.getStudentBusinessNetWorth());
        m.put("householdSize",            e.getHouseholdSize());
        m.put("numberInCollege",          e.getNumberInCollege());
        m.put("parentAgi",                e.getParentAgi());
        m.put("parentTaxesPaid",          e.getParentTaxesPaid());
        m.put("parentUntaxedIncome",      e.getParentUntaxedIncome());
        m.put("parentMaritalStatus",      e.getParentMaritalStatus());
        m.put("parentAge",                e.getParentAge());
        m.put("parentCashSavings",        e.getParentCashSavings());
        m.put("parentInvestments",        e.getParentInvestments());
        m.put("parentHomeEquity",         e.getParentHomeEquity());
        m.put("parentRetirementSavings",  e.getParentRetirementSavings());
        m.put("parentBusinessNetWorth",   e.getParentBusinessNetWorth());
        m.put("parent529Balance",         e.getParent529Balance());
        m.put("estimatedSai",             e.getEstimatedSai());
        m.put("assetRepositioning",       e.getAssetRepositioningJson());
        m.put("createdAt",                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    private static String str(Map<String, Object> b, String k, String def) {
        Object v = b.get(k);
        return (v instanceof String s && !s.isBlank()) ? s.trim() : def;
    }

    private static BigDecimal decimal(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null) return null;
        try { return new BigDecimal(v.toString().replace(",", "")); } catch (Exception e) { return null; }
    }

    private static Integer intVal(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null) return null;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}
