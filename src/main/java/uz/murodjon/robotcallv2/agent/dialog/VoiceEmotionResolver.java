package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoiceEmotion;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageOverrides;
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

    /**
     * Resolves the effective voice settings (role + adaptive speed) for the given dialog session turn.
     */
    public EffectiveVoiceSettings resolve(DialogSession s) {
        if (s == null) {
            return EffectiveVoiceSettings.NONE;
        }

        EffectiveVoiceSettings base = s.voiceSettings() != null ? s.voiceSettings() : EffectiveVoiceSettings.NONE;
        VoiceEmotion emotion = determineEmotion(s);

        StageOverrides overrides = SystemPromptFactory.overridesOf(s);
        Double effectiveSpeed = overrides != null && overrides.voiceSpeed() != null
                ? overrides.voiceSpeed()
                : agentSpeed(s, base.speed());
        if (s.emotionAdaptiveVoice()) {
            if (s.lastCustomerSentiment() == CustomerSentiment.FRUSTRATED) {
                // Calm, softer, slightly slower pace during customer frustration
                double speed = effectiveSpeed != null ? effectiveSpeed : 1.0;
                effectiveSpeed = Math.round(speed * 0.92 * 100.0) / 100.0;
            } else if (s.lastCustomerSentiment() == CustomerSentiment.CONFUSED) {
                double speed = effectiveSpeed != null ? effectiveSpeed : 1.0;
                effectiveSpeed = Math.round(speed * 0.96 * 100.0) / 100.0;
            }
        }

        String role = mapEmotionToRole(emotion);
        log.debug("[{}] Resolved voice style: state={}, sentiment={}, emotion={}, role={}, speed={}",
                s.channelId(), s.state(), s.lastCustomerSentiment(), emotion, role, effectiveSpeed);

        return new EffectiveVoiceSettings(base.provider(), effectiveSpeed, base.pitch(), role, base.fallbackVoice());
    }

    /**
     * The agent's own pace for the call's current language (NEXT-PLAN A.7) — the language
     * a call switched to included. 1.0, what every agent has until someone sets it,
     * leaves the company's speed in charge.
     */
    private static Double agentSpeed(DialogSession s, Double companySpeed) {
        AiAgent agent = s.agent();
        if (agent == null || agent.voice() == null) {
            return companySpeed;
        }
        double own = agent.voice().speedFor(s.language());
        if (own != 1.0) {
            return own;
        }
        return companySpeed;
    }

    /**
     * Determines logical emotion based on customer sentiment and scenario stage.
     */
    public VoiceEmotion determineEmotion(DialogSession s) {
        // The caller's mood steers the voice only on an agent that asked for it (emotionAdaptive);
        // it used to override every other choice whether or not the agent had.
        if (s.emotionAdaptiveVoice()) {
            CustomerSentiment sentiment = s.lastCustomerSentiment();
            if (sentiment == CustomerSentiment.FRUSTRATED) {
                return VoiceEmotion.FRIENDLY;
            }
            if (sentiment == CustomerSentiment.CONFUSED) {
                return VoiceEmotion.NEUTRAL;
            }
        }

        StageDef stage = SystemPromptFactory.stageOf(s.scenario(), s.state());
        if (stage != null && stage.emotion() != null && !stage.emotion().isBlank()) {
            return parseEmotion(stage.emotion());
        }

        // Otherwise the agent's own mood, the same for the whole call. Never guessed from the
        // stage's name: that swung one call through cheerful (GREETING), strict (DEBT_NOTICE) and
        // back to cheerful (CLOSING) — a voice that changes character three times in two minutes
        // is heard as acting, not as a person.
        AiAgent agent = s.agent();
        return agent != null && agent.voice() != null ? agent.voice().emotion() : VoiceEmotion.NEUTRAL;
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
