package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * How much of a person the agent lets into its wording. The Uzbek Yandex voices have no
 * emotional roles, so on those calls the wording is the only place warmth can come from.
 */
public enum SpeakingWarmth {
    /** One-word reactions, no sympathy beyond "Tushunaman." — the platform's original style. */
    RESERVED,
    /** A short reaction tied to what the caller said, colloquial particles; no staged sympathy. */
    BALANCED,
    /** One or two warm sentences when it fits, then back to the task. */
    WARM
}
