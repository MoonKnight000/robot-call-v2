package uz.murodjon.robotcallv2.agent.turn;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cue decides how long a caller waits and whether they get cut off, so the table is
 * the specification: things callers actually say on a debt call, and what each one
 * should do to the gate. A wrong COMPLETE here is a cut-off on a live call.
 */
class TranscriptTurnCuesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "keyingi oyning beshinchisida to'layman",
            "Ha",
            "yo'q",
            "hozir pulim yo'q",
            "bo'ladi",
            "bilmadim",
            "ertaga to'lab qo'yaman",
            "men to'lagan edim",
            "kechqurun qo'ng'iroq qiling",
            "besh yuz ming so'm",
            "to'g'ri",
            "hozir ishlayapman",
            "gaplashmoqchiman",
            "нет",
            "хорошо, заплачу завтра",
            "я не могу сейчас заплатить",
            "перезвоните вечером",
            "у меня денег нет",
            "будет на следующей неделе"
    })
    void finishedSentencesReadAsComplete(String utterance) {
        assertThat(TranscriptTurnCues.judge(utterance)).isEqualTo(TurnCue.COMPLETE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "men pulni",
            "bankga",
            "to'layman lekin",
            "keyingi oyning",
            "agar",
            "besh yuz",
            "15",
            "oldin borib",
            "pul bo'lsa",
            "shartnomani ko'rganda",
            "sizning",
            "men",
            "потому что",
            "я в",
            "пять",
            "заплачу, но",
            "если",
            "у",
            "буду на"
    })
    void unfinishedSentencesReadAsIncomplete(String utterance) {
        assertThat(TranscriptTurnCues.judge(utterance)).isEqualTo(TurnCue.INCOMPLETE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "shartnoma raqami",
            "shartnoma bormi",
            "Aziz Karimov",
            "menimcha",
            "",
            "   ",
            "кредит",
            "результат"
    })
    void neutralEndingsLeaveTheTimerAlone(String utterance) {
        assertThat(TranscriptTurnCues.judge(utterance)).isEqualTo(TurnCue.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yo'q", "Yoʻq.", "yo‘q", "YO'Q"})
    void apostropheVariantsAreTheSameWord(String utterance) {
        assertThat(TranscriptTurnCues.judge(utterance)).isEqualTo(TurnCue.COMPLETE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pulingiz", "pulingizni"})
    void aPossessiveIsNotAnImperative(String utterance) {
        assertThat(TranscriptTurnCues.judge(utterance)).isNotEqualTo(TurnCue.COMPLETE);
    }

    @org.junit.jupiter.api.Test
    void countsWordsForTheQuickAnswerRule() {
        assertThat(TranscriptTurnCues.wordCount("yo'q, ertaga")).isEqualTo(2);
        assertThat(TranscriptTurnCues.wordCount("  ha  ")).isEqualTo(1);
        assertThat(TranscriptTurnCues.wordCount(null)).isZero();
    }
}
