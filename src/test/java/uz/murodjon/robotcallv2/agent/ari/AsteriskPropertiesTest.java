package uz.murodjon.robotcallv2.agent.ari;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** An operator is rung where their phone signs in: here, or over the trunk to the operator cell. */
class AsteriskPropertiesTest {

    @Test
    void theOperatorCellRingsTheOperatorDirectly() {
        AsteriskProperties operatorCell = withOperatorTrunk("");

        assertThat(operatorCell.isOperatorCell()).isTrue();
        assertThat(operatorCell.buildOperatorDialString("op-7")).isEqualTo("PJSIP/op-7");
        assertThat(withOperatorTrunk(null).buildOperatorDialString("op-7")).isEqualTo("PJSIP/op-7");
    }

    @Test
    void anotherCellRingsTheOperatorOverTheOperatorTrunk() {
        AsteriskProperties cell = withOperatorTrunk(" operator-cell ");

        assertThat(cell.isOperatorCell()).isFalse();
        assertThat(cell.buildOperatorDialString("op-7")).isEqualTo("PJSIP/op-7@operator-cell");
    }

    private static AsteriskProperties withOperatorTrunk(String operatorTrunk) {
        return new AsteriskProperties(true, "http://localhost:8088/", "u", "p", "app", "trunk-endpoint",
                "softphone", "\\d\\d\\d\\d?", null, 30, null, 0, 0, 0, 0, operatorTrunk);
    }
}
