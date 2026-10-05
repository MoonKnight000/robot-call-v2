package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A missed agent goodbye leaves the line open until the caller drops it; a false one cuts
 * the call off mid-conversation. So this is pinned mostly by what must <em>not</em> match.
 */
class FarewellsTest {

    @Test
    void agentClosingLinesAreRecognized() {
        // Both of these were spoken on the same call, neither hung up.
        assertThat(Farewells.saidByAgent("Rahmat, kuningiz xayrli o'tsin!")).isTrue();
        assertThat(Farewells.saidByAgent("Salomat bo'ling, Murodjon aka. Xayr!")).isTrue();
        assertThat(Farewells.saidByAgent("Спасибо за ваше время, до свидания.")).isTrue();
    }

    @Test
    void aLineThatEndsInAQuestionIsNeverAGoodbye() {
        // The model is owed an answer, however politely it asked.
        assertThat(Farewells.saidByAgent("Rahmat. Yana bir savol — xayrlashaylikmi?")).isFalse();
    }

    @Test
    void aGoodbyeMentionedEarlyInALongReplyDoesNotClose() {
        // "xayr" is here, but the sentence carrying the call is the one after it.
        assertThat(Farewells.saidByAgent(
                "Xayr demasdan oldin aytib o'tay: shartnomangiz bo'yicha qarz qolmoqda, "
                        + "va uni to'lash uchun yana bir necha kun bor. Qachon to'lay olasiz")).isFalse();
    }

    @Test
    void ordinaryRepliesAreNotGoodbyes() {
        assertThat(Farewells.saidByAgent("Juda soz, ertaga 1500000 so'm to'lashingizni yozib qo'ydim.")).isFalse();
        assertThat(Farewells.saidByAgent(null)).isFalse();
        assertThat(Farewells.saidByAgent("   ")).isFalse();
    }
}
