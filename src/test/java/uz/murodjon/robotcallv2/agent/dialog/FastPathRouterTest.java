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

    @ParameterizedTest
    @ValueSource(strings = {
            "rahmat salomat bo'ling",
            "katta rahmat xayr",
            "hammasi tushunarli rahmat",
            "boshqa savolim yo'q rahmat",
            "bo'ldi rahmat"
    })
    void matchesClosingGratitudeUzbek(String phrase) {
        when(session.language()).thenReturn("uz-UZ");

        FastPathResult result = router.evaluate(session, phrase);

        assertThat(result.handled()).isTrue();
        assertThat(result.endCall()).isTrue();
        assertThat(result.reply()).contains("Arzimaydi");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "спасибо до свидания",
            "большое спасибо всего доброго",
            "все понятно спасибо",
            "нет вопросов спасибо"
    })
    void matchesClosingGratitudeRussian(String phrase) {
        when(session.language()).thenReturn("ru-RU");

        FastPathResult result = router.evaluate(session, phrase);

        assertThat(result.handled()).isTrue();
        assertThat(result.endCall()).isTrue();
        assertThat(result.reply()).contains("Пожалуйста");
    }

    @Test
    void matchesSimpleGratitudeWhenOutcomeRecorded() {
        when(session.language()).thenReturn("uz-UZ");
        when(session.turnsSinceOutcome()).thenReturn(1);

        FastPathResult result = router.evaluate(session, "rahmat");

        assertThat(result.handled()).isTrue();
        assertThat(result.endCall()).isTrue();
        assertThat(result.reply()).contains("Arzimaydi");
    }

    @Test
    void returnsNotHandledForGeneralConversation() {
        when(session.language()).thenReturn("uz-UZ");

        FastPathResult result = router.evaluate(session, "Ha, to'lovni ertaga amalga oshiraman.");

        assertThat(result.handled()).isFalse();
    }

    @Test
    void matchesRepeatUzbek() {
        when(session.language()).thenReturn("uz-UZ");
        when(session.lastAgentText()).thenReturn("Qarzingiz 500000 so'm, qachon to'laysiz?");

        FastPathResult result = router.evaluate(session, "nima dedingiz?");

        assertThat(result.handled()).isTrue();
        assertThat(result.endCall()).isFalse();
        assertThat(result.reply()).isEqualTo("Takrorlayman: Qarzingiz 500000 so'm, qachon to'laysiz?");
    }

    @Test
    void matchesIdentityUzbekWithCompanyAndLastQuestion() {
        when(session.language()).thenReturn("uz-UZ");
        when(session.companyName()).thenReturn("Uysot Bank");
        when(session.lastAgentText()).thenReturn("To'lovni ertaga qila olasizmi?");

        FastPathResult result = router.evaluate(session, "siz kimsiz o'zi?");

        assertThat(result.handled()).isTrue();
        assertThat(result.endCall()).isFalse();
        assertThat(result.reply()).contains("Uysot Bank kompaniyasining sun'iy intellekt ovozli yordamchisiman.");
        assertThat(result.reply()).endsWith("To'lovni ertaga qila olasizmi?");
    }

    @Test
    void matchesPurposeUzbek() {
        when(session.language()).thenReturn("uz-UZ");
        uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition def = mock(uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition.class);
        when(def.callPurpose()).thenReturn("qarzdorlik to'lovi");
        when(session.scenario()).thenReturn(def);
        when(session.lastAgentText()).thenReturn("Eshityapsizmi?");

        FastPathResult result = router.evaluate(session, "nega telefon qildingiz?");

        assertThat(result.handled()).isTrue();
        assertThat(result.endCall()).isFalse();
        assertThat(result.reply()).contains("qarzdorlik to'lovi masalasida bog'lanyapman.");
        assertThat(result.reply()).endsWith("Eshityapsizmi?");
    }
}
