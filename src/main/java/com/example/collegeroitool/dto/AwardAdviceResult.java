package com.example.collegeroitool.dto;

import com.example.collegeroitool.model.LowEarningSchoolEarnings;

/** Wraps AwardAssistService.getFinancialAdvice's two independent outputs: the AI-generated
 *  advice JSON, and the deterministic FSA Earnings Data Report row for the matched school (if
 *  any) — the latter is never routed through the AI, only its flag value is fed into the prompt
 *  as context so Claude can explain it. */
public class AwardAdviceResult {

    private final String adviceJson;
    private final LowEarningSchoolEarnings lowEarningData;

    public AwardAdviceResult(String adviceJson, LowEarningSchoolEarnings lowEarningData) {
        this.adviceJson = adviceJson;
        this.lowEarningData = lowEarningData;
    }

    public String getAdviceJson()                       { return adviceJson; }
    public LowEarningSchoolEarnings getLowEarningData()  { return lowEarningData; }
}
