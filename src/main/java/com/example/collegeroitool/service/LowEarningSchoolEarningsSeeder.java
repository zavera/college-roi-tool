package com.example.collegeroitool.service;

import com.example.collegeroitool.model.LowEarningSchoolEarnings;
import com.example.collegeroitool.repository.LowEarningSchoolEarningsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the FSA "Earnings Data Report" (Data Published March 2026) — a one-time hardcoded copy
 *  of the FSA's per-school "Lower Earnings" flag and underlying earnings figures, sourced from a
 *  spreadsheet export rather than a live fetch (same token/complexity tradeoff as
 *  FafsaHandbookReferenceSeeder, just bulk data instead of a few curated rows).
 *
 *  Runs on every startup (local H2 and prod Postgres alike) but loads the ~5,800 rows only when
 *  the table is empty — this is a full-dataset refresh, not a per-row idempotent seed, so an
 *  admin who has edited/removed individual rows directly in prod would have those edits
 *  overwritten only by clearing the table first.
 *
 *  TO REFRESH: replace src/main/resources/reference-data/low_earning_school_earnings.tsv with a new export
 *  of the FSA Earnings Data Report, then truncate low_earning_school_earnings in prod so this
 *  seeder reloads it on next deploy. This dataset has no automatic refresh — review it whenever
 *  the FSA publishes an updated Earnings Data Report (at minimum once a year, at the new award
 *  year cycle), consistent with the FAFSA Prep asset-repositioning table's review cadence. */
@Component
public class LowEarningSchoolEarningsSeeder {

    private static final Logger log = LoggerFactory.getLogger(LowEarningSchoolEarningsSeeder.class);
    private static final String DATA_FILE = "reference-data/low_earning_school_earnings.tsv";

    private final LowEarningSchoolEarningsRepository repository;

    public LowEarningSchoolEarningsSeeder(LowEarningSchoolEarningsRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void seed() {
        if (repository.count() > 0) return;

        List<LowEarningSchoolEarnings> batch = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(DATA_FILE).getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                LowEarningSchoolEarnings row = parseRow(line);
                if (row != null) batch.add(row);
            }
        } catch (Exception e) {
            log.warn("[low-earning-schools] Failed to load {}: {}", DATA_FILE, e.getMessage());
            return;
        }

        repository.saveAll(batch);
        log.info("[low-earning-schools] Seeded {} rows from {}", batch.size(), DATA_FILE);
    }

    private LowEarningSchoolEarnings parseRow(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 11) return null;

        LowEarningSchoolEarnings row = new LowEarningSchoolEarnings();
        row.setUnitId(parseInt(f[0]));
        if (row.getUnitId() == null) return null;
        row.setOpeId8(blankToNull(f[1]));
        row.setOpeId6(blankToNull(f[2]));
        row.setFederalSchoolCode(blankToNull(f[3]));
        row.setInstitutionName(f[4]);
        row.setNormalizedName(SchoolNameNormalizer.normalize(f[4]));
        row.setState(blankToNull(f[5]));
        row.setEarningsReported(parseDecimal(f[6]));
        row.setEarningsInflationAdjusted(parseDecimal(f[7]));
        row.setHsEarningsThresholdValue(parseDecimal(f[8]));
        row.setHsEarningsThresholdType(blankToNull(f[9]));
        row.setLowerEarningsFlag(parseBoolean(f[10]));
        return row;
    }

    private Integer parseInt(String s) {
        try { return s.isBlank() ? null : Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private BigDecimal parseDecimal(String s) {
        try { return s.isBlank() ? null : new BigDecimal(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private Boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
