package uz.murodjon.uysotvoice.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.dialog.SentimentDetector.CustomerSentiment;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.StageDef;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;

import java.util.Locale;

/**
 * Resolves effective TTS voice emotions, speaking styles (role) and adaptive speed
 * dynamically based on conversation stage and customer emotional state.
 */
@Component
public class VoiceEmotionResolver {

    private static final Logger log = LoggerFactory.getLogger(VoiceEmotionResolver.class);

    public enum VoiceEmotion {
        NEUTRAL,
        CHEERFUL,
        FRIENDLY,
        STRICT,
        WHISPER,
        SAD
    }

    /**
     * Resolves the effective voice settings (role + adaptive speed) for the given dialog session turn.
     */
    public EffectiveVoiceSettings resolve(DialogSession s) {
        if (s == null) {
            return EffectiveVoiceSettings.NONE;
        }

        EffectiveVoiceSettings base = s.voiceSettings() != null ? s.voiceSettings() : EffectiveVoiceSettings.NONE;
        VoiceEmotion emotion = determineEmotion(s);

        Double effectiveSpeed = base.speed();
        if (s.emotionAdaptiveVoice()) {
            if (s.lastCustomerSentiment() == CustomerSentiment.FRUSTRATED) {
                // Calm, softer, slightly slower pace during customer frustration
                double speed = base.speed() != null ? base.speed() : 1.0;
                effectiveSpeed = Math.round(speed * 0.92 * 100.0) / 100.0;
            } else if (s.lastCustomerSentiment() == CustomerSentiment.CONFUSED) {
                double speed = base.speed() != null ? base.speed() : 1.0;
                effectiveSpeed = Math.round(speed * 0.96 * 100.0) / 100.0;
            }
        }

        String role = mapEmotionToRole(s.ttsVoice(), emotion);
        log.debug("[{}] Resolved voice style: state={}, sentiment={}, emotion={}, role={}, speed={}",
                s.channelId(), s.state(), s.lastCustomerSentiment(), emotion, role, effectiveSpeed);

        return new EffectiveVoiceSettings(base.provider(), effectiveSpeed, base.pitch(), role);
    }

    /**
     * Determines logical emotion based on customer sentiment and scenario stage.
     */
    public VoiceEmotion determineEmotion(DialogSession s) {
        CustomerSentiment sentiment = s.lastCustomerSentiment();
        if (sentiment == CustomerSentiment.FRUSTRATED) {
            return VoiceEmotion.FRIENDLY;
        }
        if (sentiment == CustomerSentiment.CONFUSED) {
            return VoiceEmotion.NEUTRAL;
        }

        String stageId = s.state();
        if (stageId == null) {
            return VoiceEmotion.NEUTRAL;
        }

        ScenarioDefinition scenario = s.scenario();
        if (scenario != null && scenario.stages() != null) {
            for (StageDef stage : scenario.stages()) {
                if (stageId.equalsIgnoreCase(stage.id())) {
                    if (stage.emotion() != null && !stage.emotion().isBlank()) {
                        return parseEmotion(stage.emotion());
                    }
                    break;
                }
            }
        }

        return inferStageEmotion(stageId);
    }

    public VoiceEmotion parseEmotion(String text) {
        if (text == null || text.isBlank()) {
            return VoiceEmotion.NEUTRAL;
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "cheerful", "happy", "quvnoq", "xursand", "joyful" -> VoiceEmotion.CHEERFUL;
            case "friendly", "empathetic", "warm", "muloyim", "samimiy", "good" -> VoiceEmotion.FRIENDLY;
            case "strict", "serious", "jiddiy", "talabchan", "evil" -> VoiceEmotion.STRICT;
            case "whisper", "soft", "pichirlash", "yumshoq" -> VoiceEmotion.WHISPER;
            case "sad", "xafa", "hamdard" -> VoiceEmotion.SAD;
            default -> VoiceEmotion.NEUTRAL;
        };
    }

    public VoiceEmotion inferStageEmotion(String stageId) {
        if (stageId == null) {
            return VoiceEmotion.NEUTRAL;
        }
        String upper = stageId.toUpperCase(Locale.ROOT);
        if (upper.contains("GREETING") || upper.contains("SALOM")
                || upper.contains("CLOSING") || upper.contains("XAYR")
                || upper.contains("SUCCESS") || upper.contains("OFFER")
                || upper.contains("INTEREST")) {
            return VoiceEmotion.CHEERFUL;
        }
        if (upper.contains("DEBT") || upper.contains("OVERDUE")
                || upper.contains("WARNING") || upper.contains("STRICT")
                || upper.contains("DEMAND") || upper.contains("PENALTY")) {
            return VoiceEmotion.STRICT;
        }
        if (upper.contains("ESCALATE") || upper.contains("HUMAN")
                || upper.contains("TRANSFER") || upper.contains("APOLOGY")
                || upper.contains("EMPATHY")) {
            return VoiceEmotion.FRIENDLY;
        }
        return VoiceEmotion.NEUTRAL;
    }

    /**
     * Maps a logical emotion to provider-appropriate speaking role/mood.
     */
    public String mapEmotionToRole(String voiceId, VoiceEmotion emotion) {
        if (voiceId == null || voiceId.isBlank()) {
            return mapGenericRole(emotion);
        }
        String lower = voiceId.toLowerCase(Locale.ROOT);

        // Aisha voices
        if (lower.contains("gulnoza") || lower.contains("aisha")) {
            return switch (emotion) {
                case CHEERFUL -> "cheerful";
                case FRIENDLY -> "cheerful";
                case SAD -> "sad";
                case STRICT, WHISPER, NEUTRAL -> "neutral";
            };
        }

        // Yandex Uzbek v3 voices
        if (lower.equals("zamira")) {
            return switch (emotion) {
                case CHEERFUL, FRIENDLY -> "friendly";
                case STRICT -> "strict";
                default -> "neutral";
            };
        }
        if (lower.equals("yulduz")) {
            return switch (emotion) {
                case CHEERFUL, FRIENDLY -> "friendly";
                case STRICT -> "strict";
                case WHISPER -> "whisper";
                default -> "neutral";
            };
        }
        if (lower.equals("nigora")) {
            // Nigora does not support roles in SpeechKit v3
            return null;
        }

        // Yandex Russian voices
        if (lower.equals("alena") || lower.equals("filipp")) {
            return switch (emotion) {
                case CHEERFUL, FRIENDLY -> "good";
                case STRICT -> "evil";
                case WHISPER -> "whisper";
                default -> "neutral";
            };
        }
        if (lower.equals("ermil") || lower.equals("zahar") || lower.equals("jane") || lower.equals("omazh")) {
            return switch (emotion) {
                case CHEERFUL, FRIENDLY -> "good";
                case STRICT -> "evil";
                default -> "neutral";
            };
        }

        return mapGenericRole(emotion);
    }

    private String mapGenericRole(VoiceEmotion emotion) {
        return switch (emotion) {
            case CHEERFUL, FRIENDLY -> "friendly";
            case STRICT -> "strict";
            case WHISPER -> "whisper";
            case SAD -> "sad";
            default -> "neutral";
        };
    }
}
