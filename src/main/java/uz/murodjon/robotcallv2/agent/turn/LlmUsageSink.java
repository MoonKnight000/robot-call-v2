package uz.murodjon.robotcallv2.agent.turn;

/**
 * Where a side model call made during a call (the LLM turn detector) reports what it spent,
 * so the call is billed for it at that model's price.
 */
@FunctionalInterface
public interface LlmUsageSink {

    LlmUsageSink NONE = (model, prompt, completion, cached) -> { };

    void record(String model, long prompt, long completion, long cached);
}
