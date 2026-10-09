package uz.murodjon.robotcallv2.aimodel.domain.entity;

/**
 * A company's own OpenAI-compatible LLM (gap analysis 3.14). While it is set, the company's
 * cascade calls and their summaries run on it — never on the platform's model, not even as a
 * fallback: a company that brings its own model usually does so to keep the conversation away
 * from anyone else's.
 *
 * @param baseUrl      the OpenAI-compatible root, {@code /v1} included ({@code https://llm.bank.uz/v1})
 * @param model        the model every turn and every summary runs on
 * @param fastModel    the cheaper model for one-word answers and barge-in decisions; null runs
 *                     those on {@code model} too
 * @param apiKeySecret the name of the company secret holding the API key — the key itself is
 *                     never stored here
 */
public record OwnLlmEndpoint(String baseUrl, String model, String fastModel, String apiKeySecret) {
}
