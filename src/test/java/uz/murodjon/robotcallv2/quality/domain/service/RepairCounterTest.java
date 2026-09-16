package uz.murodjon.robotcallv2.quality.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepairCounterTest {

    @Test
    void everyCallerLineAskingForARepairCountsOnce() {
        String transcript = """
                AGENT: Qachon to'lay olasiz?
                CLIENT: Nima?
                AGENT: Qachon to'lay olasiz?
                CLIENT: Tushunmadim, qaytaring
                CLIENT: Ertaga
                """;

        assertThat(RepairCounter.countRepairs(transcript)).isEqualTo(2);
    }

    @Test
    void theAgentsOwnWordsAreNotCounted() {
        assertThat(RepairCounter.countRepairs("AGENT: Tushunmadim, qaytaring\nCLIENT: Ha")).isZero();
    }

    @Test
    void russianRepairsCount() {
        assertThat(RepairCounter.countRepairs("CLIENT: Что? \nCLIENT: Повторите, пожалуйста")).isEqualTo(2);
    }

    /** "Nimaga" is a question about the matter, not a request to repeat. */
    @Test
    void aWordThatOnlyStartsLikeARepairIsNotOne() {
        assertThat(RepairCounter.countRepairs("CLIENT: Nimaga?")).isZero();
    }

    @Test
    void noTranscriptIsNoRepair() {
        assertThat(RepairCounter.countRepairs(null)).isZero();
        assertThat(RepairCounter.countRepairs("")).isZero();
    }
}
