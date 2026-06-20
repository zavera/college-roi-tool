package com.example.collegeroitool.service;

import com.example.collegeroitool.dto.LlmAdviceRequest;
import com.example.collegeroitool.model.FafsaAidPackage;
import com.example.collegeroitool.repository.FafsaAidPackageRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FafsaAidPackageService {

    private final FafsaAidPackageRepository repo;

    public FafsaAidPackageService(FafsaAidPackageRepository repo) {
        this.repo = repo;
    }

    /** Upsert on (userId, aidYear). */
    public FafsaAidPackage upsert(Long userId, Integer aidYear, LlmAdviceRequest req) {
        FafsaAidPackage pkg = repo.findByUserIdAndAidYear(userId, aidYear)
            .orElseGet(() -> {
                FafsaAidPackage p = new FafsaAidPackage();
                p.setUserId(userId);
                p.setAidYear(aidYear);
                return p;
            });

        pkg.setCollegeName(req.getCollegeName());
        pkg.setMajor(req.getMajor());
        pkg.setResidency(req.getResidency());
        pkg.setLivingSituation(req.getLivingSituation());
        pkg.setCostOfAttendance(toBD(req.getComputedCOA() != null ? req.getComputedCOA() : req.getNetPrice()));
        pkg.setNetPrice(toBD(req.getComputedNetPrice() != null ? req.getComputedNetPrice() : req.getNetPrice()));
        pkg.setUnmetNeed(toBD(req.getComputedUnmetNeed()));
        pkg.setPellGrant(toBD(req.getPellGrant()));
        pkg.setInstitutionalGrant(toBD(req.getInstitutionalGrant()));
        pkg.setSubsidizedLoan(toBD(req.getSubsidizedLoan()));
        pkg.setUnsubsidizedLoan(toBD(req.getUnsubsidizedLoan()));
        pkg.setParentPlusLoan(toBD(req.getParentPlusLoan()));
        pkg.setPrivateLoan(toBD(req.getPrivateLoan()));
        pkg.setScholarshipAmount(toBD(req.getScholarshipAmount()));
        pkg.setWorkStudy(toBD(req.getWorkStudy()));
        pkg.setSixYrEarnings(toBD(req.getSixYrEarnings()));
        pkg.setCollegeWideEarnings(toBD(req.getCollegeWideEarnings()));
        pkg.setUpdatedAt(LocalDateTime.now());

        return repo.save(pkg);
    }

    /** Latest row by created_at across all aid years — for form pre-population. */
    public Optional<FafsaAidPackage> findLatest(Long userId) {
        List<FafsaAidPackage> rows = repo.findByUserIdOrderByCreatedAtDesc(userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Sum loans across all aid years (latest row per year) to populate post-grad profile defaults.
     * Returns map with "federalLoanTotal" and "privateLoanTotal".
     */
    public Map<String, BigDecimal> computeLoanDefaults(Long userId) {
        List<FafsaAidPackage> rows = repo.findLatestPerAidYear(userId);
        BigDecimal federal = BigDecimal.ZERO;
        BigDecimal priv    = BigDecimal.ZERO;
        for (FafsaAidPackage p : rows) {
            if (p.getSubsidizedLoan()   != null) federal = federal.add(p.getSubsidizedLoan());
            if (p.getUnsubsidizedLoan() != null) federal = federal.add(p.getUnsubsidizedLoan());
            if (p.getParentPlusLoan()   != null) federal = federal.add(p.getParentPlusLoan());
            if (p.getPrivateLoan()      != null) priv    = priv.add(p.getPrivateLoan());
        }
        return Map.of("federalLoanTotal", federal, "privateLoanTotal", priv);
    }

    private BigDecimal toBD(Double d) {
        return d != null ? BigDecimal.valueOf(d) : null;
    }
}
