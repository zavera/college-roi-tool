package com.example.collegeroitool.controller;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.model.Fafsa;
import com.example.collegeroitool.model.InputPayloadType;
import com.example.collegeroitool.model.ModelResponse;
import com.example.collegeroitool.repository.FafsaRepository;
import com.example.collegeroitool.repository.ModelResponseRepository;
import com.example.collegeroitool.service.GroqService;
import com.example.collegeroitool.service.TavilySearchClient;
import com.example.collegeroitool.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/fafsa-prep")
public class FafsaPrepController {

    private static final Logger log = LoggerFactory.getLogger(FafsaPrepController.class);
    private static final List<String> HANDBOOK_DOMAINS = List.of("fsapartners.ed.gov", "studentaid.gov");
    private static final String AWARD_YEAR = "2026-27";
    private static final Integer EXPECTED_TAX_YEAR = 2024; // PPY for the 2026-27 award year

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    @Value("${groq.model}")
    private String modelName;

    private final FafsaRepository fafsaRepository;
    private final ModelResponseRepository modelResponseRepository;
    private final UserService userService;
    private final GroqService groqService;
    private final TavilySearchClient tavilySearchClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FafsaPrepController(FafsaRepository fafsaRepository,
                                ModelResponseRepository modelResponseRepository,
                                UserService userService,
                                GroqService groqService,
                                TavilySearchClient tavilySearchClient) {
        this.fafsaRepository = fafsaRepository;
        this.modelResponseRepository = modelResponseRepository;
        this.userService = userService;
        this.groqService = groqService;
        this.tavilySearchClient = tavilySearchClient;
    }

    /** List all FAFSA prep entries for the current user, newest first. */
    @GetMapping
    public ResponseEntity<?> list(Principal principal) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Map<String, Object>> rows = fafsaRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
            .stream().map(this::toHistoryMap).toList();
        return ResponseEntity.ok(rows);
    }

    /** Save a new FAFSA prep entry and run the asset-repositioning analysis. */
    @PostMapping
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body, Principal principal) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(body); }
        catch (Exception e) { payloadJson = "{}"; }

        Fafsa entry = new Fafsa();
        entry.setUserId(user.getId());
        entry.setInputFafsaPayload(payloadJson);
        entry = fafsaRepository.save(entry);

        String liveContent = fetchLiveHandbookContent();

        String rawAnalysis;
        int status;
        Object parsedAnalysis = null;
        try {
            // This is a manual-entry planning tool with no document upload, so there is nothing
            // to cross-check tax years against. Pass matching expected/extracted years plus an
            // explicit note so the model doesn't flag a spurious "no tax year detected" discrepancy.
            rawAnalysis = groqService.getAssetRepositioningAdvice(payloadJson, AWARD_YEAR, liveContent,
                EXPECTED_TAX_YEAR, EXPECTED_TAX_YEAR,
                "Data entered manually by the student/family — no tax documents were uploaded for this planning session. Do not flag a tax year discrepancy; set discrepancy to null.");
            parsedAnalysis = objectMapper.readValue(GroqService.stripMarkdownFences(rawAnalysis), Object.class);
            status = 200;
        } catch (Exception e) {
            log.warn("[fafsa-prep] Groq analysis failed id={}: {}", entry.getId(), e.getMessage());
            rawAnalysis = null;
            status = 500;
        }
        logModelResponse(entry, rawAnalysis, status);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", entry.getId());
        response.put("analysis", parsedAnalysis);
        return ResponseEntity.ok(response);
    }

    /** Delete a specific entry (only the owner can delete). */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Principal principal) {
        AppUser user = resolveUser(principal);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        Optional<Fafsa> entry = fafsaRepository.findByIdAndUserId(id, user.getId());
        if (entry.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "Not found"));
        fafsaRepository.delete(entry.get());
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Fetches live FSA Handbook + studentaid.gov asset-definition content for prompt injection. */
    private String fetchLiveHandbookContent() {
        try {
            var results = tavilySearchClient.searchHandbook(
                "FAFSA SAI student aid index asset reporting rules " + AWARD_YEAR, 3, HANDBOOK_DOMAINS, 1000);
            StringBuilder sb = new StringBuilder();
            for (var r : results) {
                sb.append("\n[Source: ").append(r.get("url")).append("]\n");
                sb.append(r.get("content")).append("\n");
            }
            String content = sb.toString().trim();
            return content.isEmpty() ? null : content;
        } catch (Exception e) {
            return null;
        }
    }

    private void logModelResponse(Fafsa entry, String outputPayload, int status) {
        ModelResponse resp = new ModelResponse();
        resp.setModelName(modelName);
        resp.setTypeInputPayload(InputPayloadType.FAFSA);
        resp.setInputId(entry.getId());
        resp.setOutputPayload(outputPayload);
        resp.setResponseStatus(status);
        modelResponseRepository.save(resp);
    }

    private Map<String, Object> toHistoryMap(Fafsa entry) {
        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(entry.getInputFafsaPayload(), Map.class);
            payload = parsed;
        } catch (Exception e) {
            payload = Map.of();
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", entry.getId());
        m.put("label", payload.get("label"));
        m.put("addedBy", payload.get("addedBy"));
        m.put("createdAt", entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null);

        modelResponseRepository.findFirstByTypeInputPayloadAndInputIdOrderByCreatedAtDesc(InputPayloadType.FAFSA, entry.getId())
            .ifPresent(mr -> {
                try { m.put("analysis", objectMapper.readValue(mr.getOutputPayload(), Object.class)); }
                catch (Exception e) { m.put("analysis", null); }
            });
        return m;
    }

    private AppUser resolveUser(Principal principal) {
        if (principal == null) return devBypass ? userService.findOrCreateDevUser() : null;
        return userService.findByEmail(principal.getName()).orElse(null);
    }
}
