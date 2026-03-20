package com.example.collegeroitool.services;

import java.util.List;
import java.util.Map;

// src/main/java/com/yourapp/service/CollegeService.java (interface)
public interface CollegeService {
    List<String> searchColleges(String term);
    Map<String, Object> getCollegeROI(String name);
    List<Map<String, Object>> getCollegePrograms(String name);
    Map<String, Object> getProgramsRaw(String name);
}
