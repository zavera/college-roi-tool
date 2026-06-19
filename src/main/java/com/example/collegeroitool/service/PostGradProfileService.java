package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.DebtIntakeRequest;
import com.example.collegeroitool.model.PostGradProfile;
import com.example.collegeroitool.repository.PostGradProfileRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class PostGradProfileService {

    private final PostGradProfileRepository repo;
    private final FafsaAidPackageService aidPackageService;

    public PostGradProfileService(PostGradProfileRepository repo,
                                   FafsaAidPackageService aidPackageService) {
        this.repo = repo;
        this.aidPackageService = aidPackageService;
    }

    /** Always inserts a new row — latest by created_at wins on retrieval. */
    public PostGradProfile save(Long studentId, DebtIntakeRequest req) {
        PostGradProfile p = new PostGradProfile();
        p.setStudentId(studentId);
        p.setFederalLoanBalance(toBD(req.getFederalLoanBalance()));
        p.setPrivateLoanBalance(toBD(req.getPrivateLoanBalance()));
        p.setPrivateLender(req.getPrivateLender());
        p.setLoanServicer(req.getLoanServicer());
        p.setInterestRate(toBD(req.getInterestRate()));
        if (req.getGracePeriodEndDate() != null && !req.getGracePeriodEndDate().isBlank()) {
            try { p.setGracePeriodEndDate(LocalDate.parse(req.getGracePeriodEndDate())); }
            catch (Exception ignored) {}
        }
        p.setEmploymentStatus(req.getEmploymentStatus());
        p.setEmployerName(req.getEmployerName());
        p.setAnnualGrossIncome(toBD(req.getAnnualGrossIncome()));
        p.setHouseholdSize(req.getHouseholdSize());
        p.setMaritalStatus(req.getMaritalStatus());
        p.setCreditScoreBand(req.getCreditScoreBand());
        p.setDisabilityStatus(req.getDisabilityStatus());
        p.setSchoolAttended(req.getSchoolAttended());
        p.setUpdatedAt(LocalDateTime.now());
        return repo.save(p);
    }

    /** Most recent saved profile for this student. */
    public Optional<PostGradProfile> findLatest(Long studentId) {
        return repo.findTopByStudentIdOrderByCreatedAtDesc(studentId);
    }

    /**
     * Returns loan balance defaults for the debt relief form:
     * federalLoanBalance and privateLoanBalance summed from fafsa_aid_packages.
     * Falls back to latest post_grad_profile values if no aid packages exist.
     */
    public Map<String, BigDecimal> getDefaultBalances(Long studentId) {
        Map<String, BigDecimal> aidDefaults = aidPackageService.computeLoanDefaults(studentId);
        if (!aidDefaults.get("federalLoanTotal").equals(BigDecimal.ZERO)
                || !aidDefaults.get("privateLoanTotal").equals(BigDecimal.ZERO)) {
            return Map.of(
                "federalLoanBalance", aidDefaults.get("federalLoanTotal"),
                "privateLoanBalance", aidDefaults.get("privateLoanTotal")
            );
        }
        return findLatest(studentId)
            .map(p -> Map.of(
                "federalLoanBalance", p.getFederalLoanBalance() != null ? p.getFederalLoanBalance() : BigDecimal.ZERO,
                "privateLoanBalance", p.getPrivateLoanBalance() != null ? p.getPrivateLoanBalance() : BigDecimal.ZERO
            ))
            .orElse(Map.of("federalLoanBalance", BigDecimal.ZERO, "privateLoanBalance", BigDecimal.ZERO));
    }

    private BigDecimal toBD(Double d) {
        return d != null ? BigDecimal.valueOf(d) : null;
    }
}
