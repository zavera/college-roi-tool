package com.example.collegeroitool.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic dependency-status determination, mirroring the federal FAFSA
 * independent-student criteria: if the answer to ANY question is "yes", the
 * student is independent. Pure rules, no AI/external calls involved.
 */
@Service
public class DependencyStatusService {

    public record Question(String id, String label) {}

    private static final List<Question> QUESTIONS = List.of(
        new Question("age24", "Were you born before January 1 of five years before the award year (i.e. will you be 24 or older)?"),
        new Question("married", "Are you currently married (or separated but not divorced)?"),
        new Question("gradStudent", "Will you be working on a master's or doctorate program (e.g. MA, MBA, MD, JD, PhD, EdD) at the start of the award year?"),
        new Question("activeDuty", "Are you currently serving on active duty in the U.S. Armed Forces for purposes other than training?"),
        new Question("veteran", "Are you a veteran of the U.S. Armed Forces?"),
        new Question("hasChildren", "Do you have children who will receive more than half their support from you during the award year?"),
        new Question("hasDependents", "Do you have dependents (other than children or a spouse) who live with you and receive more than half their support from you?"),
        new Question("wardOfCourt", "At any time since you turned 13, were both your parents deceased, were you in foster care, or were you a dependent or ward of the court?"),
        new Question("emancipatedOrGuardianship", "Are you, or were you, an emancipated minor or in legal guardianship as determined by a court in your state of legal residence?"),
        new Question("homelessYouth", "Were you determined at any time on or after July 1 of last year to be an unaccompanied youth who was homeless or self-supporting and at risk of being homeless, by a school district homeless liaison, an emergency/transitional shelter program director, or a runaway/homeless youth program director?")
    );

    public List<Question> getQuestions() {
        return QUESTIONS;
    }

    /** answers maps question id -> true/false. Any true answer makes the student independent. */
    public Map<String, Object> evaluate(Map<String, Boolean> answers) {
        List<String> triggeredReasons = QUESTIONS.stream()
            .filter(q -> Boolean.TRUE.equals(answers.get(q.id())))
            .map(Question::label)
            .toList();

        String status = triggeredReasons.isEmpty() ? "dependent" : "independent";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("triggeredReasons", triggeredReasons);
        result.put("explanation", status.equals("independent")
            ? "Based on your answers, you qualify as an independent student for FAFSA purposes — only your own (and spouse's, if married) financial information is required, not your parents'."
            : "Based on your answers, you are considered a dependent student for FAFSA purposes — your parents' financial information is required on the FAFSA, even if you don't live with them or they don't claim you on their taxes.");
        return result;
    }
}
