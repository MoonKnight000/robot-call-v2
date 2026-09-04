package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.util.Locale;

/**
 * Resolves the mood a turn is spoken with — and an adaptive speed — from the scenario
 * stage and the customer's emotional state.
 *
 * <p>The mood is named in the abstract ({@code cheerful}, {@code strict}, ...); which
 * role, if any, a given voice can express it as belongs to the TTS provider, since only
 * it knows the voice that ends up speaking ({@code voice-agent.tts.yandex.voice-roles}).
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

        String role = mapEmotionToRole(emotion);
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

        // Neutral unless the scenario asked for something else. Guessing the mood from
        // the stage's name used to swing one call through cheerful (GREETING), strict
        // (DEBT_NOTICE) and back to cheerful (CLOSING) — a voice that changes character
        // three times in two minutes is heard as acting, not as a person, and a debt
        // notice read "strict" is the worst possible moment for it.
        return VoiceEmotion.NEUTRAL;
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

    /**
     * The mood name handed to the TTS provider, which matches it against the roles its
     * chosen voice declares and drops it when that voice has none.
     */
    public String mapEmotionToRole(VoiceEmotion emotion) {
        return switch (emotion) {
            case CHEERFUL -> "cheerful";
            case FRIENDLY -> "friendly";
            case STRICT -> "strict";
            case WHISPER -> "whisper";
            case SAD -> "sad";
            case NEUTRAL -> "neutral";
        };
    }
}
