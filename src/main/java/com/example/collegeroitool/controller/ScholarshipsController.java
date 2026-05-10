package com.example.collegeroitool.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves static scholarship data from scholarships.json.
 * National and community/identity entries are curated from publicly known programs —
 * not verified against live web sources on each request.
 * Field-specific entries come from the AI insights call per major.
 */
@RestController
@RequestMapping("/api/scholarships")
public class ScholarshipsController {

    private String scholarshipsJson;

    @PostConstruct
    public void load() throws IOException {
        scholarshipsJson = new String(
            new ClassPathResource("scholarships.json").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
    }

    @GetMapping("/static")
    public ResponseEntity<String> getStaticScholarships() {
        return ResponseEntity.ok()
            .header("Content-Type", "application/json")
            .header("Cache-Control", "max-age=3600")
            .body(scholarshipsJson);
    }
}
