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
        assertEquals(AmbientSound.KEYBOARD_TYPING, AmbientSound.fromString("keyboard-typing"));
        assertEquals(AmbientSound.KEYBOARD_TYPING, AmbientSound.fromString("TYPING"));
        assertEquals(AmbientSound.OFF, AmbientSound.fromString("OFF"));
        assertEquals(AmbientSound.OFF, AmbientSound.fromString(""));
        assertEquals(AmbientSound.OFF, AmbientSound.fromString(null));
    }

    @Test
    void volumeScalesTheCalibratedLevel() {
        short[] silence = new short[160];
        double full = rms(AmbientSoundGenerator.mix(silence, AmbientSound.CALL_CENTER, 4000, 1.0, 0.0));
        double half = rms(AmbientSoundGenerator.mix(silence, AmbientSound.CALL_CENTER, 4000, 0.5, 0.0));

        assertTrue(half < full, "half volume must be quieter, was " + half + " vs " + full);
        assertTrue(Math.abs(half * 2 - full) < full * 0.05,
                "half volume must be half the amplitude, was " + half + " vs " + full);
    }

    @Test
    void zeroVolumeLeavesTheFrameUntouched() {
        short[] silence = new short[160];
        assertSame(silence, AmbientSoundGenerator.mix(silence, AmbientSound.CALL_CENTER, 4000, 0.0, 0.0));
    }

    @Test
    void fadeInRampsFromSilenceToFullLevel() {
        short[] silence = new short[160];
        // Frame at the very start of a 2 s fade is silent; one past its end is at full level.
        double atStart = rms(AmbientSoundGenerator.mix(silence, AmbientSound.CALL_CENTER, 0, 1.0, 2.0));
        double afterFade = rms(AmbientSoundGenerator.mix(silence, AmbientSound.CALL_CENTER, 16000, 1.0, 2.0));
        double noFade = rms(AmbientSoundGenerator.mix(silence, AmbientSound.CALL_CENTER, 16000, 1.0, 0.0));

        assertTrue(atStart < afterFade * 0.2, "fade must start near silence, was " + atStart);
        assertEquals(noFade, afterFade, 1.0, "past the fade the bed must be at full level");
    }

    private static double rms(short[] frame) {
        double sumSq = 0;
        for (short s : frame) {
            sumSq += (double) s * s;
        }
        return Math.sqrt(sumSq / frame.length);
    }
}
