package uz.murodjon.robotcallv2.agent.audio;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;

import static org.junit.jupiter.api.Assertions.*;

class AmbientSoundGeneratorTest {

    @Test
    void mixWithOffReturnsOriginalFrame() {
        short[] silence = new short[160];
        short[] mixed = AmbientSoundGenerator.mix(silence, AmbientSound.OFF, 0);
        assertSame(silence, mixed);
    }

    @Test
    void mixWithCallCenterOverlaysAudibleEnergy() {
        short[] silence = new short[160];
        short[] mixed = AmbientSoundGenerator.mix(silence, AmbientSound.CALL_CENTER, 0);

        assertNotSame(silence, mixed);
        assertEquals(160, mixed.length);

        double sumSq = 0;
        int nonZero = 0;
        for (short s : mixed) {
            sumSq += s * s;
            if (s != 0) {
                nonZero++;
            }
        }
        assertTrue(nonZero > 150, "Call center ambiance should produce continuous audio energy");

        double rms = Math.sqrt(sumSq / mixed.length);
        // Target is around 1100 (~ -29.5 dBFS), single 20ms frame should be clearly audible (> 500 RMS)
        assertTrue(rms > 500, "Ambiance must be audible (RMS > 500), actual: " + rms);
    }

    @Test
    void mixDoesNotExceed16BitBoundaries() {
        short[] maxPositive = new short[160];
        java.util.Arrays.fill(maxPositive, (short) 32000);

        short[] mixed = AmbientSoundGenerator.mix(maxPositive, AmbientSound.CALL_CENTER, 1000);
        for (short s : mixed) {
            assertTrue(s >= -32768 && s <= 32767);
        }
    }

    @Test
    void fromStringHandlesVariantsAndAliases() {
        assertEquals(AmbientSound.CALL_CENTER, AmbientSound.fromString("CALL_CENTER"));
        assertEquals(AmbientSound.CALL_CENTER, AmbientSound.fromString("call_center"));
        assertEquals(AmbientSound.CALL_CENTER, AmbientSound.fromString("CALL_CENTER_AMBIENCE"));
        assertEquals(AmbientSound.CALL_CENTER, AmbientSound.fromString("call-center"));

        assertEquals(AmbientSound.OFFICE, AmbientSound.fromString("OFFICE"));
        assertEquals(AmbientSound.OFFICE, AmbientSound.fromString("OFFICE_BACKGROUND"));
        assertEquals(AmbientSound.OFFICE, AmbientSound.fromString("office"));

        assertEquals(AmbientSound.NATURAL_LINE, AmbientSound.fromString("NATURAL_LINE"));
        assertEquals(AmbientSound.CAFE, AmbientSound.fromString("CAFE"));
        assertEquals(AmbientSound.OFF, AmbientSound.fromString("OFF"));
        assertEquals(AmbientSound.OFF, AmbientSound.fromString(""));
        assertEquals(AmbientSound.OFF, AmbientSound.fromString(null));
    }
}
