package uz.murodjon.robotcallv2.agent.turn;

/** What {@link LlmTurnDetector} asks, and so which model it is meant to talk to. */
public enum LlmTurnMode {
    /**
     * TEN Turn Detection exactly: the fine-tuned {@code TEN_Turn_Detection} model served on an
     * OpenAI-compatible endpoint (vLLM), sent the caller's words alone and answering one token.
     * Trained on English and Chinese only.
     */
    TEN,
    /**
     * The same three answers asked of a general model — the agent's {@code fastLlmModel} —
     * with an instruction, examples and the agent's last line, which is what makes it usable
     * in Uzbek.
     */
    PROMPT
}
