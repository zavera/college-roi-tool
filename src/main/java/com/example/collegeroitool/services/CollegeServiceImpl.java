package com.example.collegeroitool.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CollegeServiceImpl implements CollegeService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${college.scorecard.api-key:hMS6qvCYkVlttQ1LaAYyeC4gPIWiUNa749oDfKFl}")
    private String apiKey;

    private static final String API_BASE = "https://api.data.gov/ed/collegescorecard/v1/schools";

    @Override
    public List<String> searchColleges(String term) {
        try {
            if (term == null || term.trim().length() < 2) {
                return List.of();
            }

            String url = API_BASE + "?api_key=" + apiKey +
                    "&school.name=" + URLEncoder.encode(term.trim(), StandardCharsets.UTF_8) +
                    "&fields=school.name&per_page=10";

            Map response = restTemplate.getForObject(url, Map.class);

            List<Map> results = (List<Map>) response.get("results");
            if (results == null || results.isEmpty()) {
                return List.of();
            }

            return results.stream()
                    .map(result -> (String) result.get("school.name"))
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .distinct()
                    .limit(10)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getCollegeROI(String name) {
        try {
            String url = API_BASE + "?api_key=" + apiKey +
                    "&school.name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) +
                    "&fields=school.name,latest.cost.attendance.academic_year,latest.cost.tuition.in_state,latest.cost.tuition.out_of_state,latest.cost.net_price.overall,latest.earnings.6_yrs_after_entry.median";

            Map response = restTemplate.getForObject(url, Map.class);
            List<Map> results = (List<Map>) response.get("results");

            if (results == null || results.isEmpty()) {
                return Map.of("error", "College not found: " + name);
            }

            Map<String, Object> result = results.get(0);
            String collegeName = (String) result.getOrDefault("school.name", name);

            // Scorecard returns flattened dot-notation keys, not nested objects
            Double costOfAttendance = result.get("latest.cost.attendance.academic_year") != null
                    ? ((Number) result.get("latest.cost.attendance.academic_year")).doubleValue() : null;

            Double inStateTuition = result.get("latest.cost.tuition.in_state") != null
                    ? ((Number) result.get("latest.cost.tuition.in_state")).doubleValue() : null;

            Double outOfStateTuition = result.get("latest.cost.tuition.out_of_state") != null
                    ? ((Number) result.get("latest.cost.tuition.out_of_state")).doubleValue() : null;

            Double netPriceValue = result.get("latest.cost.net_price.overall") != null
                    ? ((Number) result.get("latest.cost.net_price.overall")).doubleValue() : 0.0;

            Double earningsValue = result.get("latest.earnings.6_yrs_after_entry.median") != null
                    ? ((Number) result.get("latest.earnings.6_yrs_after_entry.median")).doubleValue() : 0.0;

            Map<String, Object> roiData = new java.util.HashMap<>();
            roiData.put("name", collegeName);
            roiData.put("costOfAttendance", costOfAttendance);
            roiData.put("inStateTuition", inStateTuition);
            roiData.put("outOfStateTuition", outOfStateTuition);
            roiData.put("netPrice", netPriceValue);
            roiData.put("sixYrEarnings", earningsValue);
            return roiData;

        } catch (Exception e) {
            return Map.of("error", "API request failed: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getCollegePrograms(String name) {
        try {
            String url = API_BASE + "?api_key=" + apiKey +
                    "&school.name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) +
                    "&fields=school.name,latest.programs.cip_4_digit";

            Map response = restTemplate.getForObject(url, Map.class);
            List<Map> results = (List<Map>) response.get("results");

            if (results == null || results.isEmpty()) return List.of();

            Map<String, Object> result = results.get(0);
            List<Map<String, Object>> programs =
                    (List<Map<String, Object>>) result.get("latest.programs.cip_4_digit");

            if (programs == null) return List.of();

            return programs.stream()
                    .filter(p -> p.get("title") != null)
                    .filter(p -> {
                        // Undergrad branch: only show Bachelor's and Associate's degrees
                        Map<?, ?> cred = (Map<?, ?>) p.get("credential");
                        if (cred == null) return false;
                        String credTitle = String.valueOf(cred.get("title")).toLowerCase();
                        return credTitle.contains("bachelor") || credTitle.contains("associate");
                    })
                    .map(p -> {
                        Double earnings = extractEarnings(p);
                        if (earnings == null) return null;

                        String title = (String) p.get("title");
                        Map<?, ?> credential = (Map<?, ?>) p.get("credential");
                        if (credential != null && credential.get("title") != null) {
                            title = title + " (" + credential.get("title") + ")";
                        }

                        Map<String, Object> prog = new HashMap<>();
                        prog.put("title",    title);
                        prog.put("code",     p.get("code"));
                        prog.put("earnings", earnings);
                        return prog;
                    })
                    .filter(p -> p != null)
                    .sorted((a, b) -> String.valueOf(a.get("title"))
                                           .compareTo(String.valueOf(b.get("title"))))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return List.of();
        }
    }

    private Double extractEarnings(Map<String, Object> program) {
        Object earningsObj = program.get("earnings");
        if (!(earningsObj instanceof Map)) return null;
        Map<?, ?> earningsMap = (Map<?, ?>) earningsObj;
        for (String yr : new String[]{"4_yr", "5_yr", "1_yr"}) {
            Object yrObj = earningsMap.get(yr);
            if (yrObj instanceof Map) {
                Object median = ((Map<?, ?>) yrObj).get("overall_median_earnings");
                if (median instanceof Number) return ((Number) median).doubleValue();
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> getProgramsRaw(String name) {
        try {
            String url = API_BASE + "?api_key=" + apiKey +
                    "&school.name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) +
                    "&fields=school.name,latest.programs.cip_4_digit";
            Map response = restTemplate.getForObject(url, Map.class);
            List<Map> results = (List<Map>) response.get("results");
            if (results == null || results.isEmpty()) return Map.of("error", "no results");
            // Return the first result so we can inspect every key/value
            return results.get(0);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
