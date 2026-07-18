package com.example.collegeroitool.service;

import com.example.collegeroitool.model.FafsaHandbookReference;
import com.example.collegeroitool.repository.FafsaHandbookReferenceRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Seeds the hardcoded FSA Handbook / studentaid.gov excerpts used by FAFSA Prep's
 *  asset-repositioning prompt. Runs on every startup (local H2 and prod Postgres alike,
 *  since Flyway is disabled locally) but only inserts rows missing for a given
 *  (topic, awardYear) — it never overwrites a row an admin has since edited directly in the DB. */
@Component
public class FafsaHandbookReferenceSeeder {

    private static final String AWARD_YEAR = "2026-27";

    private final FafsaHandbookReferenceRepository repository;

    public FafsaHandbookReferenceSeeder(FafsaHandbookReferenceRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void seed() {
        seedIfMissing("ppy_tax_year_rule", AWARD_YEAR,
            "FSA Handbook, AVG Ch 2 (" + AWARD_YEAR + ") - Prior-Prior Year (PPY) tax data",
            "The " + AWARD_YEAR + " FAFSA uses Prior-Prior Year (PPY) tax data - always the tax year "
                + "2 years before the academic year start (e.g. 2024 tax returns for the " + AWARD_YEAR
                + " award year). Assets, however, are reported as of the date the FAFSA is signed, not "
                + "as of the tax year.",
            "https://fsapartners.ed.gov/knowledge-center/fsa-handbook/" + AWARD_YEAR
                + "/application-and-verification-guide/ch2-federal-methodology");

        seedIfMissing("sai_asset_formula", AWARD_YEAR,
            "FSA Handbook, AVG Ch 3 (" + AWARD_YEAR + ") - SAI asset assessment rates",
            "Parent assets (cash, savings, checking, and net worth of investments) are assessed at up "
                + "to a 12% rate toward the parent contribution in the SAI formula. Student assets are "
                + "assessed at a higher 20% rate. Excluded from reportable net worth of investments: the "
                + "family's primary residence, and the value of qualified retirement plans (401(k), IRA, "
                + "403(b), pension). Under the One Big Beautiful Bill Act changes reflected in the "
                + AWARD_YEAR + " AVG, the net worth of a family-owned business or investment farm must "
                + "be reported (this removed the prior small-business exclusion for businesses with "
                + "fewer than 100 employees) - the principal place of residence is still excluded even "
                + "if it sits on farm/business property. 529 plan balances owned by a parent are "
                + "assessed at the lower parent rate (up to 5.64%) rather than the student rate (20%).",
            "https://fsapartners.ed.gov/knowledge-center/fsa-handbook/" + AWARD_YEAR
                + "/application-and-verification-guide/ch3-student-aid-index-sai-and-pell-grant-eligibility");

        seedIfMissing("studentaid_net_worth_definitions", AWARD_YEAR,
            "studentaid.gov - \"Current Net Worth of Investments\" definitions",
            "studentaid.gov's asset definitions (plain-English companion to the FSA Handbook): "
                + "\"Current Net Worth of Investments\" includes real estate other than the primary "
                + "residence, stocks, bonds, mutual funds, CDs, UGMA/UTMA accounts, trusts, and "
                + "qualified education benefits (529 plans, Coverdell ESAs). It excludes the value of "
                + "retirement plans (401(k), pensions, non-education IRAs), life insurance, and ABLE "
                + "accounts. Cash, savings, and checking balances are reported separately from "
                + "investment net worth, at the current balance as of the day the FAFSA is signed.",
            "https://studentaid.gov/help/current-net-worth");
    }

    private void seedIfMissing(String topic, String awardYear, String chapterLabel,
                                String content, String sourceUrl) {
        if (repository.findByTopicAndAwardYear(topic, awardYear).isPresent()) return;
        FafsaHandbookReference ref = new FafsaHandbookReference();
        ref.setTopic(topic);
        ref.setAwardYear(awardYear);
        ref.setChapterLabel(chapterLabel);
        ref.setContent(content);
        ref.setSourceUrl(sourceUrl);
        repository.save(ref);
    }
}
