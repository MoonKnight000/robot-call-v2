package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmStillThereLineTest {

    @Test
    void aCheckAboutTheConversationIsKept() {
        assertThat(LlmTurnClient.acceptStillThereLine(" \"Aloqadamisiz? Uy necha xonali bo'lsin, deb so'rayotgan edim.\" "))
                .isEqualTo("Aloqadamisiz? Uy necha xonali bo'lsin, deb so'rayotgan edim.");
        assertThat(LlmTurnClient.acceptStillThereLine("Алло, вы меня слышите?")).isEqualTo("Алло, вы меня слышите?");
    }

    @Test
    void nothingToSayFallsBackToTheCannedLine() {
        assertThat(LlmTurnClient.acceptStillThereLine("-")).isNull();
        assertThat(LlmTurnClient.acceptStillThereLine("  ")).isNull();
        assertThat(LlmTurnClient.acceptStillThereLine(null)).isNull();
    }

    @Test
    void aCheckThatAsksNothingQuotesAFigureOrRunsOnIsRefused() {
        assertThat(LlmTurnClient.acceptStillThereLine("Siz bilan gaplashib turibman.")).isNull();
        assertThat(LlmTurnClient.acceptStillThereLine("Alo? Qarzingiz 500 ming so'm edi.")).isNull();
        assertThat(LlmTurnClient.acceptStillThereLine("Alo, eshityapsizmi? Biz hozir siz bilan Chilonzor va Yunusobod "
                + "tumanlaridagi uch xonali uylar haqida, ularning narxi va to'lov shartlari haqida gaplashayotgan edik, davom etamizmi?"))
                .isNull();
    }
}
