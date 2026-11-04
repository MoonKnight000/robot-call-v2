package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmBridgeLineTest {

    @Test
    void aFewWordsAboutWhatTheCallerSaidAreKept() {
        assertThat(LlmTurnClient.acceptBridgeLine(" \"Uch xonali, yaxshi.\" ")).isEqualTo("Uch xonali, yaxshi.");
        assertThat(LlmTurnClient.acceptBridgeLine("Kechirasiz, uch xonali dedingiz.")).isEqualTo("Kechirasiz, uch xonali dedingiz.");
    }

    @Test
    void nothingToSayFallsBackToTheCannedLine() {
        assertThat(LlmTurnClient.acceptBridgeLine("-")).isNull();
        assertThat(LlmTurnClient.acceptBridgeLine("  ")).isNull();
        assertThat(LlmTurnClient.acceptBridgeLine(null)).isNull();
    }

    @Test
    void aBridgeThatAsksAnswersOrQuotesAFigureIsRefused() {
        assertThat(LlmTurnClient.acceptBridgeLine("Qaysi hudud sizga ma'qul?")).isNull();
        assertThat(LlmTurnClient.acceptBridgeLine("Qarzingiz 500 ming so'm.")).isNull();
        assertThat(LlmTurnClient.acceptBridgeLine(
                "Tushunarli, uch xonali uylarimiz Chilonzor va Yunusobodda bor, ko'rib chiqamiz.")).isNull();
    }

    @Test
    void aBridgeThatParrotsTheCallerIsRefused() {
        assertThat(LlmTurnClient.acceptBridgeLine("Navoiydan, tushundim.", "navoiydan hozir yotudim")).isNull();
        assertThat(LlmTurnClient.acceptBridgeLine("Uch xonali, tushundim.", "uch xonali")).isNull();
        assertThat(LlmTurnClient.acceptBridgeLine("Navoiy shahridan, xo'p.", "navoiydan qarayapman")).isNull();
    }

    @Test
    void aPlainAcknowledgementOrAnApologyIsKept() {
        assertThat(LlmTurnClient.acceptBridgeLine("Xo'p.", "uch xonali")).isEqualTo("Xo'p.");
        assertThat(LlmTurnClient.acceptBridgeLine("Kechirasiz, uch xonali dedingiz.", "uch xonali dedim-ku"))
                .isEqualTo("Kechirasiz, uch xonali dedingiz.");
    }
}
