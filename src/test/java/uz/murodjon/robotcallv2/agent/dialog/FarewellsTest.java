package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two directions fail in opposite ways. A missed agent goodbye leaves the line open
 * until the caller drops it; a false one cuts the call off mid-conversation. A missed
 * caller sign-off costs a filler and a redundant turn; a false one hangs up on somebody
 * who was still talking. So both sides are pinned mostly by what must <em>not</em> match.
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

    @Test
    void callerSignOffsAreRecognized() {
        // "рахмет" is what the recognizer actually returned on a uz-UZ call.
        assertThat(Farewells.saidByCaller("рахмет")).isTrue();
        assertThat(Farewells.saidByCaller("Rahmat.")).isTrue();
        assertThat(Farewells.saidByCaller("katta rahmat sizga")).isTrue();
        assertThat(Farewells.saidByCaller("спасибо вам")).isTrue();
        assertThat(Farewells.saidByCaller("yaxshi qoling")).isTrue();
    }

    @Test
    void thanksThatCarriesAnAnswerIsNotASignOff() {
        // The reason the word cap exists: the sign-off is not the message here.
        assertThat(Farewells.saidByCaller("rahmat, ertaga to'layman")).isFalse();
        assertThat(Farewells.saidByCaller("спасибо, но я не смогу заплатить")).isFalse();
    }

    @Test
    void politeFillerAloneNeverEndsACall() {
        // The reason a core word is required: on its own each of these means nothing,
        // and a recognizer produces them out of noise.
        assertThat(Farewells.saidByCaller("до")).isFalse();
        assertThat(Farewells.saidByCaller("ham")).isFalse();
        assertThat(Farewells.saidByCaller("yaxshi")).isFalse();
    }

    @Test
    void agreementIsNotASignOff() {
        // These belong to Backchannels; ending the call on them loses the answer after.
        assertThat(Farewells.saidByCaller("ha")).isFalse();
        assertThat(Farewells.saidByCaller("tushunarli")).isFalse();
        assertThat(Farewells.saidByCaller(null)).isFalse();
    }
}
