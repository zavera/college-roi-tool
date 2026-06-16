package com.example.collegeroitool.controller;

import com.example.collegeroitool.dto.DebtIntakeRequest;
import com.example.collegeroitool.service.DebtManagementService;
import com.example.collegeroitool.service.GroqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debt")
public class DebtManagementController {

    private final DebtManagementService debtService;
    private final GroqService groqService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DebtManagementController(DebtManagementService debtService, GroqService groqService) {
        this.debtService = debtService;
        this.groqService = groqService;
    }

    @PostMapping("/repayment-plans")
    public ResponseEntity<?> getRepaymentPlans(@RequestBody DebtIntakeRequest req) {
        try {
            List<Map<String, Object>> plans = debtService.calculateRepaymentPlans(req);
            Map<String, Object> pslfResult = null;
            if (req.getEmployerName() != null && !req.getEmployerName().isBlank()) {
                pslfResult = debtService.checkPslfEligibility(req.getEmployerName());
            }
            String aiJson = groqService.getRepaymentRecommendation(req, plans, pslfResult);
            Object aiParsed;
            try {
                aiParsed = objectMapper.readValue(aiJson, Object.class);
            } catch (Exception e) {
                aiParsed = Map.of("rationale", aiJson);
            }
            return ResponseEntity.ok(Map.of(
                "plans",      plans,
                "pslf",       pslfResult != null ? pslfResult : Map.of(),
                "aiAdvice",   aiParsed
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not calculate repayment plans: " + e.getMessage()));
        }
    }

    @PostMapping("/pslf-check")
    public ResponseEntity<?> checkPslf(@RequestBody Map<String, String> body) {
        try {
            String employer = body.get("employerName");
            return ResponseEntity.ok(debtService.checkPslfEligibility(employer));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "PSLF check failed: " + e.getMessage()));
        }
    }

    @PostMapping("/hardship-letter")
    public ResponseEntity<?> generateHardshipLetter(@RequestBody DebtIntakeRequest req) {
        try {
            String letter = groqService.getHardshipLetter(req);
            // Split letter and checklist
            String letterText   = letter;
            String checklistText = "";
            int splitIdx = letter.indexOf("---CHECKLIST---");
            if (splitIdx >= 0) {
                letterText   = letter.substring(0, splitIdx).trim();
                checklistText = letter.substring(splitIdx + 15).trim();
            }
            return ResponseEntity.ok(Map.of(
                "letter",    letterText,
                "checklist", checklistText
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Could not generate hardship letter: " + e.getMessage()));
        }
    }
}
