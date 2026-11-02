package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** An argument the model searched with, as the call variable a tool's result saves it under. */
class HttpToolExecutorArgumentTextTest {

    @Test
    void aListIsJoinedAndANumberLosesItsTrailingZeros() {
        assertThat(HttpToolExecutor.argumentText(List.of("2", "3"))).isEqualTo("2, 3");
        assertThat(HttpToolExecutor.argumentText(60000.0)).isEqualTo("60000");
        assertThat(HttpToolExecutor.argumentText(78.50)).isEqualTo("78.5");
        assertThat(HttpToolExecutor.argumentText(" Yunusobod ")).isEqualTo("Yunusobod");
    }

    @Test
    void nothingToSaveIsNull() {
        assertThat(HttpToolExecutor.argumentText(null)).isNull();
        assertThat(HttpToolExecutor.argumentText("  ")).isNull();
        assertThat(HttpToolExecutor.argumentText(List.of())).isNull();
        assertThat(HttpToolExecutor.argumentText(Arrays.asList(null, " "))).isNull();
    }
}
