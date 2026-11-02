package uz.murodjon.robotcallv2.agent.turn;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the agent asked decides how long the caller's answer is waited out — pinned on the
 * lines of a recorded lead call.
 */
class AgentQuestionCuesTest {

    @Test
    void aYesNoQuestionIsClosed() {
        assertThat(AgentQuestionCues.classify("Assalomu alaykum, Murodjon! Men Uysot kompaniyasidanman, "
                + "yangi uylarimizga ariza qoldirgan ekansiz — ikki daqiqa gaplashishga vaqtingiz bormi?"))
                .isEqualTo(AgentQuestion.CLOSED);
        assertThat(AgentQuestionCues.classify("Удобно ли вам сейчас говорить?")).isEqualTo(AgentQuestion.CLOSED);
    }

    @Test
    void aPickFromOptionsIsClosed() {
        assertThat(AgentQuestionCues.classify("Ajoyib. Murodjon aka, o'zingizga necha xonali uy qarayapsiz?"))
                .isEqualTo(AgentQuestion.CLOSED);
        assertThat(AgentQuestionCues.classify("Qaysi turar joy majmuamiz sizga ko'proq ma'qul?"))
                .isEqualTo(AgentQuestion.CLOSED);
    }

    @Test
    void aWhyIsOpenEvenWithAPickInIt() {
        assertThat(AgentQuestionCues.classify("To'lov muddati o'tgan. Nima sababdan to'lanmadi?"))
                .isEqualTo(AgentQuestion.OPEN);
        assertThat(AgentQuestionCues.classify("Почему вы выбрали этот или другой вариант?"))
                .isEqualTo(AgentQuestion.OPEN);
    }

    @Test
    void onlyTheLastQuestionCounts() {
        assertThat(AgentQuestionCues.classify("Nega bunday bo'ldi? Mayli. Ertaga to'lay olasizmi?"))
                .isEqualTo(AgentQuestion.CLOSED);
    }

    @Test
    void aLineWithoutAQuestionAsksNothing() {
        assertThat(AgentQuestionCues.classify("Rahmat, ma'lumotlarni yozib oldim. Salomat bo'ling!"))
                .isEqualTo(AgentQuestion.NONE);
        assertThat(AgentQuestionCues.classify(null)).isEqualTo(AgentQuestion.NONE);
    }

    @Test
    void aQuestionTheRulesCannotReadIsLeftAlone() {
        assertThat(AgentQuestionCues.classify("Qanday to'lamoqchisiz?")).isEqualTo(AgentQuestion.NONE);
    }
}
