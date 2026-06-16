package com.example.collegeroitool.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simplified, deterministic Student Aid Index (SAI) calculator approximating the
 * post-2024 FAFSA Simplification Act formula. This is an ESTIMATE for planning
 * purposes only — it is not the official Department of Education calculation,
 * which uses additional regional/state tax allowances and exact published
 * bracket tables that change annually. Key simplification-era rules applied:
 * SAI is no longer divided by the number of family members in college, and
 * there is no parent asset protection allowance (flat conversion rate instead).
 */
@Service
public class SaiCalculatorService {

    public static final double SAI_FLOOR = -1500;
    private static final double STUDENT_INCOME_PROTECTION_ALLOWANCE = 10550;
    private static final double STUDENT_INCOME_ASSESSMENT_RATE = 0.50;
    private static final double STUDENT_ASSET_ASSESSMENT_RATE = 0.20;
    private static final double HOUSEHOLD_ASSET_ASSESSMENT_RATE = 0.12;

    public record YearInput(
        int year,
        double parentAgi, double parentUntaxedIncome, double parentTaxesPaid, double parentAssets,
        double studentAgi, double studentUntaxedIncome, double studentAssets,
        int familySize
    ) {}

    public Map<String, Object> calculateYear(YearInput input, String dependencyStatus) {
        boolean independent = "independent".equals(dependencyStatus);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", input.year());

        if (independent) {
            double householdIncome = Math.max(0,
                input.studentAgi() + input.studentUntaxedIncome() - input.parentTaxesPaid());
            double householdContribution = householdContribution(householdIncome, input.studentAssets(), input.familySize());
            double sai = Math.max(SAI_FLOOR, householdContribution);
            result.put("dependencyStatus", "independent");
            result.put("householdContribution", round(householdContribution));
            result.put("sai", round(sai));
        } else {
            double parentAvailableIncome = Math.max(0,
                input.parentAgi() + input.parentUntaxedIncome() - input.parentTaxesPaid());
            double parentContribution = householdContribution(parentAvailableIncome, input.parentAssets(), input.familySize());

            double studentAvailableIncome = Math.max(0,
                input.studentAgi() + input.studentUntaxedIncome() - STUDENT_INCOME_PROTECTION_ALLOWANCE);
            double studentContributionFromIncome = studentAvailableIncome * STUDENT_INCOME_ASSESSMENT_RATE;
            double studentContributionFromAssets = Math.max(0, input.studentAssets()) * STUDENT_ASSET_ASSESSMENT_RATE;
            double studentContribution = studentContributionFromIncome + studentContributionFromAssets;

            double sai = Math.max(SAI_FLOOR, parentContribution + studentContribution);

            result.put("dependencyStatus", "dependent");
            result.put("parentContribution", round(parentContribution));
            result.put("studentContribution", round(studentContribution));
            result.put("sai", round(sai));
        }
        return result;
    }

    public List<Map<String, Object>> projectMultiYear(List<YearInput> years, String dependencyStatus) {
        return years.stream().map(y -> calculateYear(y, dependencyStatus)).toList();
    }

    /** Shared "available income + assets -> contribution" math used for parents and for independent-student households. */
    private double householdContribution(double availableIncomeBeforeIpa, double assets, int familySize) {
        double aai = availableIncomeBeforeIpa - incomeProtectionAllowance(familySize);
        double contributionFromIncome = contributionFromIncomeBracket(aai);
        double contributionFromAssets = Math.max(0, assets) * HOUSEHOLD_ASSET_ASSESSMENT_RATE;
        return contributionFromIncome + contributionFromAssets;
    }

    /** Simplified Income Protection Allowance table by family size (2024-25 approximate published figures). */
    private double incomeProtectionAllowance(int familySize) {
        int size = Math.max(1, familySize);
        return switch (size) {
            case 1 -> 17220;
            case 2 -> 23520;
            case 3 -> 29180;
            case 4 -> 36000;
            case 5 -> 42440;
            case 6 -> 49650;
            default -> 49650 + (size - 6) * 6000;
        };
    }

    /** Simplified graduated assessment-rate bracket table applied to Adjusted Available Income (AAI). */
    private double contributionFromIncomeBracket(double aai) {
        if (aai <= 0) return Math.max(SAI_FLOOR, aai * 0.22);
        if (aai <= 18000) return aai * 0.22;
        if (aai <= 23000) return 18000 * 0.22 + (aai - 18000) * 0.25;
        if (aai <= 27500) return 18000 * 0.22 + 5000 * 0.25 + (aai - 23000) * 0.29;
        if (aai <= 32500) return 18000 * 0.22 + 5000 * 0.25 + 4500 * 0.29 + (aai - 27500) * 0.34;
        if (aai <= 37500) return 18000 * 0.22 + 5000 * 0.25 + 4500 * 0.29 + 5000 * 0.34 + (aai - 32500) * 0.40;
        return 18000 * 0.22 + 5000 * 0.25 + 4500 * 0.29 + 5000 * 0.34 + 5000 * 0.40 + (aai - 37500) * 0.47;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
