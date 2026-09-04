package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FastPathRouterTest {

    private FastPathRouter router;
    private DialogSession session;

    @BeforeEach
    void setUp() {
        router = new FastPathRouter();
        session = mock(DialogSession.class);
        when(session.channelId()).thenReturn("chan-123");
        when(session.isEnded()).thenReturn(false);
    }

    @Test
    void returnsNotHandledForNullOrBlankInput() {
        assertThat(router.evaluate(session, null)).isEqualTo(FastPathResult.NOT_HANDLED);
        assertThat(router.evaluate(session, "")).isEqualTo(FastPathResult.NOT_HANDLED);
        assertThat(router.evaluate(session, "   ")).isEqualTo(FastPathResult.NOT_HANDLED);
    }

    @Test
    void returnsNotHandledWhenSessionIsEnded() {
        when(session.isEnded()).thenReturn(true);
        assertThat(router.evaluate(session, "adashdingiz")).isEqualTo(FastPathResult.NOT_HANDLED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "men emasman",
            "adashdingiz",
            "boshqa odam bu",
            "notogri tushdingiz",
            "noto'g'ri tushdingiz",
            "noto’g’ri tushdingiz",
            "bu yerda yashamaydi"
    })
    void matchesWrongPersonUzbek(String phrase) {
        when(session.language()).thenReturn("uz-UZ");

        FastPathResult result = router.evaluate(session, phrase);

        assertThat(result.handled()).isTrue();
        assertThat(result.disposition()).isEqualTo(Disposition.WRONG_NUMBER);
        assertThat(result.reply()).contains("Kechirasiz, adashibmiz");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "вы ошиблись",
            "не туда попали",
            "здесь таких нет",
            "неправильный номер"
    })
    void matchesWrongPersonRussian(String phrase) {
        when(session.language()).thenReturn("ru-RU");

        FastPathResult result = router.evaluate(session, phrase);

        assertThat(result.handled()).isTrue();
        assertThat(result.disposition()).isEqualTo(Disposition.WRONG_NUMBER);
        assertThat(result.reply()).contains("Извините, мы ошиблись номером");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "boshqa telefon qilmang",
            "telefon qilmang",
            "qaytib qilmang",
            "iltimos bezovta qilmang",
            "bu spam"
    })
    void matchesDoNotCallUzbek(String phrase) {
        when(session.language()).thenReturn("uz-UZ");

        FastPathResult result = router.evaluate(session, phrase);

        assertThat(result.handled()).isTrue();
        assertThat(result.disposition()).isEqualTo(Disposition.DO_NOT_CALL);
        assertThat(result.reply()).contains("raqamingiz ro'yxatdan chiqariladi");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "не звоните больше",
            "удалите мой номер",
            "не беспокойте"
    })
    void matchesDoNotCallRussian(String phrase) {
        when(session.language()).thenReturn("ru-RU");

        FastPathResult result = router.evaluate(session, phrase);

        assertThat(result.handled()).isTrue();
        assertThat(result.disposition()).isEqualTo(Disposition.DO_NOT_CALL);
        assertThat(result.reply()).contains("ваш номер исключен из базы");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "operatorga ulang",
            "operator bilan gaplashmoqchiman",
            "tirik odam bormi"
    })
    void matchesTransferUzbek(String phrase) {
        when(session.language()).thenReturn("uz-UZ");

        FastPathResult result = router.evaluate(session, phrase);

        assertThat(result.handled()).isTrue();
        assertThat(result.disposition()).isEqualTo(Disposition.TRANSFERRED);
    }

    @Test
    void returnsNotHandledForGeneralConversation() {
        when(session.language()).thenReturn("uz-UZ");

        FastPathResult result = router.evaluate(session, "Ha, to'lovni ertaga amalga oshiraman.");

        assertThat(result.handled()).isFalse();
    }
}
