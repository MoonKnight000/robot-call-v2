package uz.murodjon.robotcallv2.mcp.domain.entity;

import java.util.List;
import java.util.Map;

/**
 * One question asked of many calls, counted: how many said yes, no or could not tell, which calls
 * those were, and a few quoted lines as evidence.
 *
 * @param asked     calls the question was asked of
 * @param counts    {@code yes} / {@code no} / {@code unclear} → how many
 * @param callIds   the same keys → the calls
 * @param evidence  up to five short quotes per answer, each prefixed with its call id
 * @param chargedUzs what the questions cost
 */
public record CallQuestionAnswer(String question, int asked, Map<String, Integer> counts,
                                 Map<String, List<Long>> callIds, Map<String, List<String>> evidence,
                                 long chargedUzs) {
}
