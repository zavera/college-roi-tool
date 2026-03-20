package com.example.collegeroitool.controller;

import com.example.collegeroitool.services.CollegeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/colleges")
public class CollegeController {

    private final CollegeService collegeService;

    // Constructor injection (Spring best practice)
    public CollegeController(CollegeService collegeService) {
        this.collegeService = collegeService;
    }

    @GetMapping("/search")
    public List<String> search(@RequestParam String term) {
        return collegeService.searchColleges(term);
    }

    @GetMapping("/{name}")
    public Map<String, Object> getROI(@PathVariable String name) {
        return collegeService.getCollegeROI(name);
    }

    @GetMapping("/{name}/programs")
    public List<Map<String, Object>> getPrograms(@PathVariable String name) {
        return collegeService.getCollegePrograms(name);
    }

    /** Temporary debug endpoint — returns the raw Scorecard API result so we can inspect field names */
    @GetMapping("/{name}/programs/raw")
    public Map<String, Object> getProgramsRaw(@PathVariable String name) {
        return collegeService.getProgramsRaw(name);
    }
}
