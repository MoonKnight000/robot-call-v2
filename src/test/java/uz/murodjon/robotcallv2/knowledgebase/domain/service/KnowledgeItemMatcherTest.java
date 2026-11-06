package uz.murodjon.robotcallv2.knowledgebase.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A curated answer is read out word for word, so a wrong pick must be harder to get than no pick. */
class KnowledgeItemMatcherTest {

    private static final KnowledgeItem PAYMENT = item(1L, "payment", "to'lov, click", "Click orqali to'lang.", "Через Click.");
    private static final KnowledgeItem LEGAL = item(2L, "legal", "sud, kim", "Sud qarori bo'yicha.", null);

    @Test
    void aKeywordMustStartAWordNotSitInsideOne() {
        // "kim" is inside "hokim" — the old substring match spoke the legal answer here.
        assertThat(KnowledgeItemMatcher.findBest("Hokimiyatga borganman", List.of(LEGAL), Map.of(), 0.8, "uz-UZ")).isNull();
        // An inflected word still matches by its start: "to'lovni" starts with "to'lov".
        KnowledgeItemMatch match = KnowledgeItemMatcher.findBest("To'lovni qachon qilaman?", List.of(PAYMENT, LEGAL),
                Map.of(), 0.8, "uz-UZ");
        assertThat(match).isNotNull();
        assertThat(match.itemId()).isEqualTo(1L);
        assertThat(match.byKeyword()).isTrue();
    }

    @Test
    void apostropheVariantsAreOneWord() {
        assertThat(KnowledgeItemMatcher.findBest("tolov haqida", List.of(PAYMENT), Map.of(), 0.8, "uz-UZ")).isNotNull();
        assertThat(KnowledgeItemMatcher.findBest("toʻlov haqida", List.of(PAYMENT), Map.of(), 0.8, "uz-UZ")).isNotNull();
    }

    @Test
    void meaningAloneMustClearTheItemThreshold() {
        assertThat(KnowledgeItemMatcher.findBest("Pulni qanday yuboraman", List.of(PAYMENT), Map.of(1L, 0.79), 0.8, "uz-UZ"))
                .isNull();
        KnowledgeItemMatch match = KnowledgeItemMatcher.findBest("Pulni qanday yuboraman", List.of(PAYMENT),
                Map.of(1L, 0.83), 0.8, "ru-RU");
        assertThat(match).isNotNull();
        assertThat(match.byKeyword()).isFalse();
        assertThat(match.answer()).isEqualTo("Через Click.");
    }

    @Test
    void aKeywordMatchBeatsAMeaningOnlyMatchAndMeaningBreaksKeywordTies() {
        KnowledgeItem other = item(3L, "click-fees", "click", "Click komissiyasi yo'q.", null);
        KnowledgeItemMatch match = KnowledgeItemMatcher.findBest("Click komissiya bormi",
                List.of(PAYMENT, other, LEGAL), Map.of(1L, 0.70, 3L, 0.88, 2L, 0.95), 0.8, "uz-UZ");

        assertThat(match.itemId()).isEqualTo(3L);
        assertThat(match.byKeyword()).isTrue();
    }

    @Test
    void keywordsShorterThanThreeLettersAreIgnored() {
        KnowledgeItem shortKey = item(4L, "id", "ha", "Javob.", null);
        assertThat(KnowledgeItemMatcher.findBest("ha mayli", List.of(shortKey), Map.of(), 0.8, "uz-UZ")).isNull();
    }

    private static KnowledgeItem item(long id, String key, String keywords, String answerUz, String answerRu) {
        return new KnowledgeItem(id, 1L, null, key, "topic", "Title", answerUz, answerRu, null, keywords, true,
                Instant.now(), Instant.now());
    }
}
