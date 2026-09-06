package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

public final class KnowledgeValidator {

    private KnowledgeValidator() {
    }

    public static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ValidationException(ErrorCode.KNOWLEDGE_KEY_BLANK);
        }
    }

    public static void validateAnswer(String answerUz) {
        if (answerUz == null || answerUz.isBlank()) {
            throw new ValidationException(ErrorCode.KNOWLEDGE_ANSWER_BLANK);
        }
    }
}
