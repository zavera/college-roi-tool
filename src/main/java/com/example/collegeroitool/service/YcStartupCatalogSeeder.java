package com.example.collegeroitool.service;

import com.example.collegeroitool.model.YcStartupCatalog;
import com.example.collegeroitool.repository.YcStartupCatalogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the active-company seed catalog from the public yc-oss community mirror of YC's company
 *  directory (https://yc-oss.github.io/api/companies/all.json, no signup/API key required) — the
 *  deterministic "real companies" backbone for Startup Locator's personalized search, per the
 *  standard build-vs-buy guidance for this kind of feature: don't try to build a full startup
 *  database (that's what Pitchbook/CB Insights charge $12k+/year for); seed from a free structured
 *  source, then layer Tavily on top per-candidate for freshness (funding news, hiring confirmation).
 *
 *  Runs on every startup (local H2 and prod Postgres alike) but loads all ~4,200 rows only when
 *  the table is empty — a full-dataset refresh, not a per-row idempotent seed (same tradeoff as
 *  LowEarningSchoolEarningsSeeder).
 *
 *  TO REFRESH: re-fetch https://yc-oss.github.io/api/companies/all.json, filter to
 *  status == "Active", trim/flatten into the same TSV columns, replace
 *  src/main/resources/reference-data/yc_startup_catalog.tsv, then truncate yc_startup_catalog in
 *  prod so this seeder reloads it on next deploy. No automatic refresh — company hiring status
 *  and industry tags drift; review periodically (this dataset moves faster than the FSA earnings
 *  data, since YC company status/hiring changes far more often than federal earnings reporting). */
@Component
public class YcStartupCatalogSeeder {

    private static final Logger log = LoggerFactory.getLogger(YcStartupCatalogSeeder.class);
    private static final String DATA_FILE = "reference-data/yc_startup_catalog.tsv";

    private final YcStartupCatalogRepository repository;

    public YcStartupCatalogSeeder(YcStartupCatalogRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void seed() {
        if (repository.count() > 0) return;

        List<YcStartupCatalog> batch = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(DATA_FILE).getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                YcStartupCatalog row = parseRow(line);
                if (row != null) batch.add(row);
            }
        } catch (Exception e) {
            log.warn("[yc-startup-catalog] Failed to load {}: {}", DATA_FILE, e.getMessage());
            return;
        }

        repository.saveAll(batch);
        log.info("[yc-startup-catalog] Seeded {} rows from {}", batch.size(), DATA_FILE);
    }

    private YcStartupCatalog parseRow(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 14) return null;

        YcStartupCatalog row = new YcStartupCatalog();
        row.setName(f[0]);
        if (row.getName() == null || row.getName().isBlank()) return null;
        row.setOneLiner(blankToNull(f[1]));
        row.setDescription(blankToNull(f[2]));
        row.setWebsite(blankToNull(f[3]));
        row.setAllLocations(truncate(blankToNull(f[4]), 500));
        row.setRegions(truncate(blankToNull(f[5]), 500));
        row.setIndustry(blankToNull(f[6]));
        row.setSubindustry(blankToNull(f[7]));
        row.setTags(truncate(blankToNull(f[8]), 500));
        row.setTeamSize(parseInt(f[9]));
        row.setBatch(blankToNull(f[10]));
        row.setStage(blankToNull(f[11]));
        row.setHiring("true".equalsIgnoreCase(f[12]));
        row.setYcUrl(blankToNull(f[13]));
        return row;
    }

    private Integer parseInt(String s) {
        try { return s.isBlank() ? null : Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    // Defensive truncation to the DB column length — protects against a future re-export/refresh
    // exceeding the column width and taking down the whole seed with an insert failure.
    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
