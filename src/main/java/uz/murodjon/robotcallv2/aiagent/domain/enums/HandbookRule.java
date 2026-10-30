package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * A best-practice instruction an agent can switch on in the studio's Agent Handbook. Each one
 * adds a rule to the prompt on top of the platform's own (platform-guardrails.txt) — none of
 * them repeats or loosens one, so an agent with an empty handbook speaks exactly as before.
 * The tone itself is {@link SpeakingWarmth}, not a rule here.
 *
 * <p>The rule's wording is in {@code agent/dialog/HandbookPrompt}. Removing a constant breaks
 * stored agents and published snapshots that name it — retire one by keeping it and dropping
 * its prompt line instead.
 */
public enum HandbookRule {
    /** Personality: an upset caller hears one short, specific acknowledgement before the answer. */
    HIGH_EMPATHY,
    /** Accuracy: names, numbers, dates and addresses the caller dictates are read back and confirmed. */
    CONFIRM_DETAILS,
    /** Accuracy: codes, order numbers and phone numbers are said slowly, in short groups. */
    SPELL_OUT_CODES,
    /** Accuracy: letters of names, emails and IDs are spelled with a familiar word per letter, then confirmed. */
    PHONETIC_SPELLING,
    /** Accuracy: numbers, dates, times and money are written out as they are said aloud, not as digits. */
    SPOKEN_NUMBERS,
    /** Accuracy: near-variants of the same name, street or word are treated as one thing, not asked about again. */
    SMART_MATCHING,
    /** Accuracy: company facts (prices, terms, stock) only from the knowledge base, tools or the prompt. */
    KNOWLEDGE_ONLY,
    /** Safety: the instructions, tools and the fact of a script are never revealed or discussed. */
    PROTECT_INSTRUCTIONS,
    /** Safety: off-topic requests are declined politely and the call returns to its purpose. */
    STAY_ON_TOPIC,
    /** Safety: no medical, legal or financial advice beyond the company's own terms. */
    NO_PROFESSIONAL_ADVICE
}
