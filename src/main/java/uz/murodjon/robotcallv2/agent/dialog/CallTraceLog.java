package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageBranch;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.BranchOperator;
import uz.murodjon.robotcallv2.scenario.domain.service.StageBranchEvaluator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The call's block-by-block trace on its timeline — what the call debug view reads
 * ({@code GET /api/reports/calls/{id}/trace}): every block the call entered and why
 * ({@link CallEventType#STAGE_ENTERED}), every value the call recorded
 * ({@link CallEventType#FACT_RECORDED}), and every function the model ran with what it was sent
 * and what came back ({@link CallEventType#TOOL_CALLED}). Text is redacted like the transcript.
 * Times are {@code offsetMs} from the dialog's start — the transcript lines' clock.
 *
 * <p>A chat conversation writes the same trace ({@code GET /api/chats/{id}/trace}): its session
 * carries a {@code chatSessionId} instead of a call attempt. A chat session is rebuilt for every
 * message, so its {@code offsetMs} counts from that message, and {@code at} is the clock to read.
 * A session with neither (the studio's test runs) records nothing.
 */
@Component
public class CallTraceLog {

    /** A search result can run to pages; the trace shows the head of it. */
    private static final int MAX_RESULT_CHARS = 4000;
    private static final int PASSAGE_PREVIEW_CHARS = 300;

    private final CallEventUseCase callEventUseCase;
    private final ObjectMapper objectMapper;

    public CallTraceLog(CallEventUseCase callEventUseCase, ObjectMapper objectMapper) {
        this.callEventUseCase = callEventUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * @param result what the tool returned to the model; null when it threw
     */
    public void recordToolCall(long companyId, long callAttemptId, long chatSessionId, AiAgent agent, Instant startedAt,
                               String stage, String toolName, String argumentsJson, String result, long durationMs) {
        String arguments = PiiRedactor.redact(argumentsJson, agent);
        String safeResult = result == null ? null : cut(PiiRedactor.redact(result, agent));
        write(companyId, callAttemptId, chatSessionId, CallEventType.TOOL_CALLED,
                "toolName", toolName,
                "stage", stage,
                "arguments", parsed(arguments),
                "result", safeResult,
                "success", result != null && !result.startsWith("ERROR:"),
                "durationMs", durationMs,
                "offsetMs", offsetMs(startedAt));
    }

    /**
     * The call moved from {@code from} to {@code to}. How it got there is read off the flow: the
     * Logic Split branch that matched (with the fact values it compared), the condition on the
     * edge the model took, or the target's global condition when no edge leads there.
     */
    public void recordStageEntered(DialogOutcomeSink s, long companyId, long callAttemptId, long chatSessionId,
                                   Instant startedAt, String from, String to) {
        ScenarioDefinition scenario = s.scenario();
        StageDef fromStage = scenario == null || from == null ? null : scenario.findStage(from);
        StageDef toStage = scenario == null || to == null ? null : scenario.findStage(to);
        Map<String, Object> facts = StageEntry.variables(s);

        String via;
        String branch = null;
        Map<String, Object> compared = null;
        String condition = null;
        StageBranch matched = fromStage == null ? null : findMatchedBranch(fromStage.branches(), facts, to);
        if (from == null) {
            via = "START";
        } else if (matched != null) {
            via = "BRANCH";
            branch = describe(matched);
            if (matched.fact() != null) {
                compared = new LinkedHashMap<>();
                compared.put(matched.fact(), facts.get(matched.fact()));
            }
        } else if (fromStage != null && fromStage.transitionConditions() != null
                && fromStage.transitionConditions().containsKey(to)) {
            via = "CONDITION";
            condition = fromStage.transitionConditions().get(to);
        } else if (fromStage != null && (fromStage.allowedTransitions() == null || !fromStage.allowedTransitions().contains(to))
                && toStage != null && toStage.globalCondition() != null && !toStage.globalCondition().isBlank()) {
            via = "GLOBAL";
            condition = toStage.globalCondition();
        } else {
            via = "EDGE";
        }
        write(companyId, callAttemptId, chatSessionId, CallEventType.STAGE_ENTERED,
                "stage", to,
                "title", toStage != null ? toStage.title() : null,
                "type", toStage != null && toStage.type() != null ? toStage.type().name() : null,
                "from", from,
                "via", via,
                "branch", branch,
                "facts", compared,
                "condition", condition,
                "offsetMs", offsetMs(startedAt));
    }

    /** A value the call recorded (a tool's outcome, a mapped response field, an extracted variable). */
    public void recordFact(DialogOutcomeSink s, long companyId, long callAttemptId, long chatSessionId, AiAgent agent,
                           Instant startedAt, String key, Object value) {
        Object safeValue = value instanceof String text ? PiiRedactor.redact(text, agent) : value;
        write(companyId, callAttemptId, chatSessionId, CallEventType.FACT_RECORDED,
                "key", key,
                "value", safeValue instanceof Number || safeValue instanceof Boolean || safeValue instanceof String
                        ? safeValue : String.valueOf(safeValue),
                "stage", s.state(),
                "offsetMs", offsetMs(startedAt));
    }

    /**
     * The request a function sent and what came back, as it went over the wire: method, address and
     * body with the company's secrets masked, the header names (their values may be credentials), the
     * HTTP status — or the error when nothing came back — and the response body, cut like a tool result.
     *
     * @param status null when the request failed before an answer
     */
    public void recordHttpExchange(long companyId, long callAttemptId, long chatSessionId, String toolName,
                                   String method, String url,
                                   List<String> headerNames, String requestBody, Integer status, String responseBody,
                                   String error, long durationMs) {
        write(companyId, callAttemptId, chatSessionId, CallEventType.TOOL_HTTP,
                "toolName", toolName,
                "method", method,
                "url", url,
                "headers", headerNames == null || headerNames.isEmpty() ? null : headerNames,
                "requestBody", parsed(cut(PiiRedactor.redact(requestBody))),
                "status", status,
                "responseBody", cut(PiiRedactor.redact(responseBody)),
                "error", error,
                "durationMs", durationMs);
    }

    /** One model request's tokens, as billed — prompt (of it, served from cache), completion and thinking. */
    public void recordLlmUsage(DialogSession s, long promptTokens, long cachedTokens, long completionTokens,
                               long thinkingTokens) {
        write(s.companyId(), s.callAttemptId(), s.chatSessionId(), CallEventType.LLM_USAGE,
                "stage", s.state(),
                "promptTokens", promptTokens,
                "cachedTokens", cachedTokens,
                "completionTokens", completionTokens,
                "thinkingTokens", thinkingTokens,
                "offsetMs", offsetMs(s.startedAt()));
    }

    /**
     * What the knowledge base gave this turn: the curated answer spoken verbatim, or the passages the model was
     * handed (each with its source and score, the text cut to a preview) — or nothing, which is the gap to fill.
     */
    public void recordKnowledgeRetrieval(DialogSession s, String question, KnowledgeItemMatch curated,
                                         List<KnowledgePassage> passages, int limit, long durationMs) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (KnowledgePassage passage : passages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunkId", passage.chunkId());
            row.put("sourceId", passage.sourceId());
            row.put("sourceName", passage.sourceName());
            if (passage.pageUrl() != null) {
                row.put("pageUrl", passage.pageUrl());
            }
            row.put("score", round(passage.score()));
            row.put("preview", preview(passage.content()));
            found.add(row);
        }
        write(s.companyId(), s.callAttemptId(), s.chatSessionId(), CallEventType.KNOWLEDGE_RETRIEVED,
                "agentId", s.agent() != null ? s.agent().id() : null,
                "stage", s.state(),
                "question", PiiRedactor.redact(question, s.agent()),
                "curatedAnswer", curated != null ? curated.answer() : null,
                "curatedItemId", curated != null ? curated.itemId() : null,
                "curatedMatchedBy", curated == null ? null : curated.byKeyword() ? "KEYWORD" : "MEANING",
                "curatedScore", curated != null ? round(curated.score()) : null,
                "passages", found,
                "found", curated != null || !found.isEmpty(),
                "limit", limit,
                "durationMs", durationMs,
                "offsetMs", offsetMs(s.startedAt()));
    }

    /**
     * What the agent was told about the client before it said a word — the memory block of its prompt, as it
     * stood when the conversation started: the newest conversations it heard about, the facts and the notes.
     */
    public void recordMemoryRecalled(long companyId, long callAttemptId, long chatSessionId, ClientMemory memory) {
        if (memory == null || memory.isEmpty()) {
            return;
        }
        List<Map<String, Object>> calls = new ArrayList<>();
        for (RememberedCall call : memory.recentCalls()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", call.id());
            row.put("at", call.at() != null ? call.at().toString() : null);
            row.put("scenarioKey", call.scenarioKey());
            row.put("disposition", call.disposition() != null ? call.disposition().name() : null);
            row.put("summary", call.summary());
            row.put("callAttemptId", call.callAttemptId());
            row.put("chatSessionId", call.chatSessionId());
            calls.add(row);
        }
        Map<String, Object> facts = new LinkedHashMap<>();
        memory.facts().forEach((key, value) -> facts.put(key, value != null ? value.toString() : null));
        write(companyId, callAttemptId, chatSessionId, CallEventType.MEMORY_RECALLED,
                "preferredName", memory.preferredName(),
                "preferredLanguage", memory.preferredLanguage(),
                "operatorNotes", memory.operatorNotes(),
                "calls", calls,
                "facts", facts);
    }

    /** Onto the call's trace, else the chat's; a session with neither is a test run and keeps nothing. */
    private void write(long companyId, long callAttemptId, long chatSessionId, CallEventType type, Object... keyValues) {
        if (callAttemptId != 0) {
            callEventUseCase.record(companyId, callAttemptId, type, keyValues);
        } else if (chatSessionId != 0) {
            callEventUseCase.recordForChat(companyId, chatSessionId, type, keyValues);
        }
    }

    private static double round(double score) {
        return Math.round(score * 1000) / 1000.0;
    }

    /** A passage in the trace is there to be recognised, not read: its opening words are enough. */
    private static String preview(String content) {
        return content != null && content.length() > PASSAGE_PREVIEW_CHARS
                ? content.substring(0, PASSAGE_PREVIEW_CHARS) + "…" : content;
    }

    private static String cut(String text) {
        return text != null && text.length() > MAX_RESULT_CHARS ? text.substring(0, MAX_RESULT_CHARS) + "…" : text;
    }

    /** The first branch that holds, if it is the one that sent the call to {@code to}. */
    private static StageBranch findMatchedBranch(List<StageBranch> branches, Map<String, Object> facts, String to) {
        if (branches == null) {
            return null;
        }
        for (StageBranch branch : branches) {
            if (branch != null && branch.operator() != null && StageBranchEvaluator.findTarget(List.of(branch), facts::get) != null) {
                return to != null && to.equals(branch.goTo()) ? branch : null;
            }
        }
        return null;
    }

    private static String describe(StageBranch branch) {
        BranchOperator operator = branch.operator() == null ? BranchOperator.ALWAYS : branch.operator();
        return switch (operator) {
            case ALWAYS -> "ALWAYS";
            case EXISTS, MISSING -> branch.fact() + " " + operator.name();
            default -> branch.fact() + " " + operator.name() + " " + branch.value();
        };
    }

    private static Long offsetMs(Instant startedAt) {
        return startedAt == null ? null : Duration.between(startedAt, Instant.now()).toMillis();
    }

    /** The arguments as the object the model sent, so the history shows fields; the text when it is not JSON. */
    private Object parsed(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(argumentsJson, Object.class);
        } catch (Exception e) {
            return argumentsJson;
        }
    }
}
