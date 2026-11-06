package uz.murodjon.robotcallv2.mcp.application.service;

import jakarta.validation.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.TokenUsage;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelTokens;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.mcp.domain.entity.CallQuestionAnswer;
import uz.murodjon.robotcallv2.report.application.dto.CallRow;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * {@code ask_calls}: one yes/no question asked of each of up to {@value #MAX_CALLS} calls by the
 * summary's model ("did the caller object to the price?"), counted. The calls are picked by the
 * same filter as the call list — a random sample by default — so the share it reports stands for
 * the period, not for the newest calls. Each question is charged to the company.
 */
@Component
@Order(60)
public class CallQuestionMcpToolSet implements McpToolSet {

    static final int MAX_CALLS = 50;
    private static final int MAX_EVIDENCE = 5;
    private static final Set<Permission> CALL_READ = EnumSet.of(Permission.REPORT_READ);
    private static final String SYSTEM = """
            You read one phone call transcript (AGENT / CLIENT lines) and answer one question about it.
            Answer ONLY with JSON: {"answer": "yes" | "no" | "unclear", "evidence": "<the shortest quote
            from the transcript that shows it, or empty>"}. "unclear" when the transcript does not say.
            QUESTION: %s""";

    private final ReportUseCase reportUseCase;
    private final CallTranscriptUseCase callTranscriptUseCase;
    private final SummaryService summaryService;
    private final AiModelConfigUseCase aiModelConfigUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final McpToolArguments arguments;

    public CallQuestionMcpToolSet(ReportUseCase reportUseCase, CallTranscriptUseCase callTranscriptUseCase,
                                  SummaryService summaryService, AiModelConfigUseCase aiModelConfigUseCase,
                                  CallBillingUseCase callBillingUseCase, Validator validator) {
        this.reportUseCase = reportUseCase;
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.summaryService = summaryService;
        this.aiModelConfigUseCase = aiModelConfigUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.arguments = new McpToolArguments(validator);
    }

    @Override
    public List<McpToolDefinition> findTools() {
        return List.of(new McpToolDefinition("ask_calls", """
                Ask one yes/no question of many calls and get it counted: yes / no / unclear with the call \
                ids and short quotes. Calls are chosen by the list_calls filter (a random sample unless \
                randomSample is false), at most %d. Costs money per call — say how many calls it will read \
                before running it.""".formatted(MAX_CALLS), CALL_READ, """
                {"type": "object", "required": ["question"], "properties": {
                  "question": {"type": "string", "maxLength": 500, "description": "One yes/no question, e.g. Did the client object to the price?"},
                  "size": {"type": "integer", "minimum": 1, "maximum": %d},
                  "campaignId": {"type": "integer"},
                  "aiAgentId": {"type": "integer"},
                  "disposition": {"type": "string"},
                  "sentiment": {"type": "string", "enum": ["POSITIVE", "NEUTRAL", "NEGATIVE", "HOSTILE"]},
                  "dateFrom": {"type": "string", "format": "date-time"},
                  "dateTo": {"type": "string", "format": "date-time"},
                  "durationMinSec": {"type": "integer", "minimum": 0},
                  "randomSample": {"type": "boolean"},
                  "ids": {"type": "array", "items": {"type": "integer"}, "maxItems": %d, "description": "Exactly these calls instead of a filter"}}}"""
                .formatted(MAX_CALLS, MAX_CALLS),
                (companyId, tool, args, draftOnly) -> {
                    String question = arguments.requireText(tool, args, "question");
                    var filterArgs = args.deepCopy();
                    filterArgs.remove("question");
                    if (!filterArgs.has("randomSample")) {
                        filterArgs.put("randomSample", true);
                    }
                    if (!filterArgs.has("size")) {
                        filterArgs.put("size", filterArgs.path("ids").isArray() ? Math.max(1, filterArgs.get("ids").size()) : 20);
                    }
                    CallFilter filter = arguments.convert(tool, filterArgs, CallFilter.class);
                    return ask(companyId, question, reportUseCase.findCalls(companyId, filter).data());
                }));
    }

    private CallQuestionAnswer ask(long companyId, String question, List<CallRow> calls) {
        if (!summaryService.isAvailable()) {
            throw new ConflictException(ErrorCode.COPILOT_NOT_CONFIGURED);
        }
        List<CallRow> picked = calls.size() > MAX_CALLS ? calls.subList(0, MAX_CALLS) : calls;
        String system = SYSTEM.formatted(question);
        List<Future<Map<String, Object>>> answers = new ArrayList<>();
        List<TokenUsage> usages = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (CallRow call : picked) {
                TokenUsage usage = new TokenUsage();
                usages.add(usage);
                answers.add(executor.submit(() -> summaryService.askAboutCall(companyId, system,
                        callTranscriptUseCase.findTranscriptText(call.callId()), usage)));
            }
        }
        boolean ownLlm = aiModelConfigUseCase.findEffectiveByCompanyId(companyId).ownLlm() != null;
        String batchKey = UUID.randomUUID().toString();
        Map<String, Integer> counts = new LinkedHashMap<>(Map.of("yes", 0, "no", 0, "unclear", 0));
        Map<String, List<Long>> callIds = new LinkedHashMap<>();
        Map<String, List<String>> evidence = new LinkedHashMap<>();
        long charged = 0;
        for (int i = 0; i < picked.size(); i++) {
            long callId = picked.get(i).callId();
            String answer = normalise(join(answers.get(i)));
            counts.merge(answer, 1, Integer::sum);
            callIds.computeIfAbsent(answer, key -> new ArrayList<>()).add(callId);
            Object quote = answer.equals("unclear") ? null : join(answers.get(i)).get("evidence");
            List<String> quotes = evidence.computeIfAbsent(answer, key -> new ArrayList<>());
            if (quote != null && !quote.toString().isBlank() && quotes.size() < MAX_EVIDENCE) {
                quotes.add("#" + callId + ": " + quote);
            }
            if (!ownLlm) {
                TokenUsage usage = usages.get(i);
                charged += callBillingUseCase.chargeCallQuestion(companyId, callId, batchKey,
                        CallUsage.ofModel(new ModelTokens(usage.model(), usage.promptTokens(),
                                usage.completionTokens(), 0)));
            }
        }
        return new CallQuestionAnswer(question, picked.size(), counts, callIds, evidence, charged);
    }

    private static Map<String, Object> join(Future<Map<String, Object>> answer) {
        try {
            Map<String, Object> value = answer.get();
            return value != null ? value : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String normalise(Map<String, Object> answer) {
        Object value = answer.get("answer");
        String text = value != null ? value.toString().trim().toLowerCase() : "";
        return text.equals("yes") || text.equals("no") ? text : "unclear";
    }
}
