package uz.murodjon.robotcallv2.agent.tts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StressMarksTest {

    @Test
    void stripsMarksBeforeLetters() {
        assertThat(StressMarks.strip("Sar+oy turar joy majmuasi")).isEqualTo("Saroy turar joy majmuasi");
        assertThat(StressMarks.strip("з+амок и зам+ок")).isEqualTo("замок и замок");
        assertThat(StressMarks.strip("+o'zbek")).isEqualTo("o'zbek");
    }

    @Test
    void leavesPlusThatIsNotAStressMark() {
        assertThat(StressMarks.strip("+998 90 123")).isEqualTo("+998 90 123");
        assertThat(StressMarks.strip("2+x")).isEqualTo("2+x");
        assertThat(StressMarks.strip("A + B")).isEqualTo("A + B");
        assertThat(StressMarks.strip(null)).isNull();
    }
}
