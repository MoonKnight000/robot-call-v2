package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.PronunciationRule;

import java.util.List;

/**
 * DTO for Pronunciation / Phonetic Dictionary tab.
 */
public record AgentPronunciationDto(
        List<PronunciationRule> rules
) {
    public List<PronunciationRule> rulesOrEmpty() {
        return rules != null ? rules : List.of();
    }
}
