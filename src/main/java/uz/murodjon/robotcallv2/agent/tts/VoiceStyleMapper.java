package uz.murodjon.robotcallv2.agent.tts;

import com.fasterxml.jackson.databind.node.ObjectNode;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.Locale;

/**
 * One mood and one pace ({@link EffectiveVoiceSettings#role()}, {@link EffectiveVoiceSettings#speed()},
 * named by {@code VoiceEmotion}) turned into what each TTS engine takes. Yandex and Aisha match the mood
 * against their voices' own roles themselves; the engines here have no roles, only their own controls.
 *
 * <p>A turn with no mood and no pace of its own sends nothing, so the voice keeps the settings it
 * was designed (or cloned) with.
 */
final class VoiceStyleMapper {

    private VoiceStyleMapper() {
    }

    /** The mood the turn asked for, lower case; null for none or neutral. */
    static String findMood(EffectiveVoiceSettings style) {
        if (style == null || style.role() == null || style.role().isBlank()) {
            return null;
        }
        String mood = style.role().trim().toLowerCase(Locale.ROOT);
        return "neutral".equals(mood) ? null : mood;
    }

    /** The turn's pace within the engine's range; null when it is the voice's own (1.0) or unset. */
    static Double findSpeed(EffectiveVoiceSettings style, double min, double max) {
        if (style == null || style.speed() == null || style.speed() == 1.0) {
            return null;
        }
        return Math.max(min, Math.min(max, style.speed()));
    }

    /**
     * ElevenLabs {@code voice_settings}: a lower stability and some style exaggeration for a lively
     * mood, a steadier voice for a firm one; speed within 0.7–1.2.
     */
    static void putElevenLabsVoiceSettings(ObjectNode root, EffectiveVoiceSettings style) {
        String mood = findMood(style);
        Double speed = findSpeed(style, 0.7, 1.2);
        if (mood == null && speed == null) {
            return;
        }
        double[] stabilityAndStyle = switch (mood == null ? "" : mood) {
            case "cheerful" -> new double[]{0.35, 0.45};
            case "friendly" -> new double[]{0.45, 0.25};
            case "strict" -> new double[]{0.70, 0.0};
            case "sad" -> new double[]{0.50, 0.20};
            case "whisper" -> new double[]{0.60, 0.0};
            default -> new double[]{0.50, 0.0};
        };
        ObjectNode settings = root.putObject("voice_settings");
        settings.put("stability", stabilityAndStyle[0]);
        settings.put("similarity_boost", 0.75);
        settings.put("style", stabilityAndStyle[1]);
        if (speed != null) {
            settings.put("speed", speed);
        }
    }

    /**
     * Cartesia {@code generation_config} (sonic-3 and newer only — earlier models reject it):
     * the nearest of its emotions and a speed within 0.6–1.5.
     */
    static void putCartesiaGenerationConfig(ObjectNode root, String model, EffectiveVoiceSettings style) {
        if (model == null || !model.toLowerCase(Locale.ROOT).startsWith("sonic-3")) {
            return;
        }
        String mood = findMood(style);
        Double speed = findSpeed(style, 0.6, 1.5);
        String emotion = mood == null ? null : switch (mood) {
            case "cheerful" -> "happy";
            case "friendly" -> "content";
            case "strict" -> "determined";
            case "sad" -> "sad";
            case "whisper" -> "calm";
            default -> null;
        };
        if (emotion == null && speed == null) {
            return;
        }
        ObjectNode config = root.putObject("generation_config");
        if (speed != null) {
            config.put("speed", speed);
        }
        if (emotion != null) {
            config.put("emotion", emotion);
        }
    }

    /** The mood as words, for an engine steered by an instruction (Gemini, OpenAI); null for none. */
    static String findTone(EffectiveVoiceSettings style) {
        String mood = findMood(style);
        if (mood == null) {
            return null;
        }
        return switch (mood) {
            case "cheerful" -> "cheerful, upbeat, smiling";
            case "friendly" -> "warm, friendly and caring";
            case "strict" -> "calm, serious and firm";
            case "sad" -> "soft and sympathetic";
            case "whisper" -> "quiet and soft, close to a whisper";
            default -> null;
        };
    }

    /**
     * Gemini TTS is steered by the text it reads: "Say in a … tone: …" is its documented form. Null
     * when the turn has neither a mood nor a pace, and the text then goes as it is.
     */
    static String findGeminiDirection(EffectiveVoiceSettings style) {
        String tone = findTone(style);
        Double speed = findSpeed(style, 0.5, 2.0);
        String pace = speed == null ? null : speed < 1.0 ? "a little slower than usual" : "a little faster than usual";
        if (tone == null && pace == null) {
            return null;
        }
        StringBuilder direction = new StringBuilder("Say");
        if (tone != null) {
            direction.append(" in a ").append(tone).append(" tone");
        }
        if (pace != null) {
            direction.append(tone != null ? ", " : " ").append(pace);
        }
        return direction.append(": ").toString();
    }
}
