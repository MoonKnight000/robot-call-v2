package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.insight.application.port.input.AgentInsightUseCase;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsightFilter;
import uz.murodjon.robotcallv2.mcp.domain.entity.CallPeriodComparison;
import uz.murodjon.robotcallv2.quality.application.port.input.CallReviewUseCase;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.report.application.dto.CallDetail;
import uz.murodjon.robotcallv2.report.application.port.input.ContactCenterReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.DashboardReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSummary;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counted figures for the copilot's call analysis. Counting and percentages are done by the
 * database, over every call of the period — never by the model over a page of examples. The
 * same use cases the dashboard and the Reports page read, so the copilot and the charts agree.
 */
@Component
@Order(10)
public class CallAnalyticsMcpToolSet implements McpToolSet {

    private static final Set<Permission> CALL_READ = EnumSet.of(Permission.REPORT_READ);
    private static final Set<Permission> AGENT_READ = EnumSet.of(Permission.AI_AGENT_READ);

    private static final String PERIOD = """
            "from": {"type": "string", "description": "ISO-8601 instant or YYYY-MM-DD (company's zone); omit for 7 days before 'to'"},
            "to": {"type": "string", "description": "ISO-8601 instant or YYYY-MM-DD, exclusive; omit for now"}""";

    private final DashboardReportUseCase dashboardReportUseCase;
    private final ContactCenterReportUseCase contactCenterReportUseCase;
    private final AgentInsightUseCase agentInsightUseCase;
    private final ReportUseCase reportUseCase;
    private final CallEventUseCase callEventUseCase;
    private final CallReviewUseCase callReviewUseCase;
    private final McpToolArguments arguments;

    public CallAnalyticsMcpToolSet(DashboardReportUseCase dashboardReportUseCase,
                                   ContactCenterReportUseCase contactCenterReportUseCase,
                                   AgentInsightUseCase agentInsightUseCase,
                                   ReportUseCase reportUseCase,
                                   CallEventUseCase callEventUseCase,
                                   CallReviewUseCase callReviewUseCase,
                                   Validator validator) {
        this.dashboardReportUseCase = dashboardReportUseCase;
        this.contactCenterReportUseCase = contactCenterReportUseCase;
        this.agentInsightUseCase = agentInsightUseCase;
        this.reportUseCase = reportUseCase;
        this.callEventUseCase = callEventUseCase;
        this.callReviewUseCase = callReviewUseCase;
        this.arguments = new McpToolArguments(validator);
    }

    @Override
    public List<McpToolDefinition> findTools() {
        return List.of(
                new McpToolDefinition("get_call_stats", """
                        Counted figures over EVERY call of a period: totals, answered, average duration, \
                        outcome counts, funnel, per-campaign rows, and the KPI change against the previous \
                        equal-length period. Start every 'how many / what share / did it grow' question here.""",
                        CALL_READ, """
                        {"type": "object", "properties": {%s,
                          "campaignId": {"type": "integer", "description": "Only this campaign's calls"}}}""".formatted(PERIOD),
                        this::findCallStats),

                new McpToolDefinition("compare_periods", """
                        Two periods side by side with the differences computed: call count change %, answer \
                        rate change in points, average duration change %, and every outcome's share change \
                        in points. Omit compareFrom/compareTo to compare with the equal-length period just before.""",
                        CALL_READ, """
                        {"type": "object", "properties": {%s,
                          "compareFrom": {"type": "string"},
                          "compareTo": {"type": "string"},
                          "campaignId": {"type": "integer"}}}""".formatted(PERIOD),
                        this::comparePeriods),

                new McpToolDefinition("get_call_timeseries", """
                        Calls per hour or per day over a period (total, answered, no answer, error, average \
                        duration) — to find WHEN a change started.""", CALL_READ, """
                        {"type": "object", "properties": {%s, "campaignId": {"type": "integer"}}}""".formatted(PERIOD),
                        (companyId, tool, args, draftOnly) -> dashboardReportUseCase.findDashboardTimeseries(companyId,
                                arguments.optionalText(args, "from"), arguments.optionalText(args, "to"),
                                arguments.optionalId(tool, args, "campaignId"))),

                new McpToolDefinition("get_stage_funnel", """
                        For one agent's scenario: how many calls reached each stage, dropped there, and \
                        converted after it — WHERE the conversation is lost.""", CALL_READ, """
                        {"type": "object", "required": ["agentId"], "properties": {%s,
                          "agentId": {"type": "integer"},
                          "campaignId": {"type": "integer"}}}""".formatted(PERIOD),
                        (companyId, tool, args, draftOnly) -> dashboardReportUseCase.findStageFunnel(companyId,
                                arguments.optionalText(args, "from"), arguments.optionalText(args, "to"),
                                arguments.requireId(tool, args, "agentId"), arguments.optionalId(tool, args, "campaignId"))),

                new McpToolDefinition("get_call_topics", """
                        What the AI calls were about: topics with call, converted and escalated counts — \
                        WHAT callers raise.""", CALL_READ, """
                        {"type": "object", "properties": {%s,
                          "agentId": {"type": "integer"},
                          "campaignId": {"type": "integer"},
                          "limit": {"type": "integer", "minimum": 1, "maximum": 50}}}""".formatted(PERIOD),
                        (companyId, tool, args, draftOnly) -> dashboardReportUseCase.findAgentCallTopics(companyId,
                                arguments.optionalText(args, "from"), arguments.optionalText(args, "to"),
                                arguments.optionalId(tool, args, "agentId"), arguments.optionalId(tool, args, "campaignId"),
                                arguments.optionalInt(args, "limit"))),

                new McpToolDefinition("get_hourly_heatmap", """
                        Calls and answer rate by weekday (1 = Monday) and hour, company's zone — the best \
                        time to dial.""", CALL_READ, """
                        {"type": "object", "properties": {%s, "campaignId": {"type": "integer"}}}""".formatted(PERIOD),
                        (companyId, tool, args, draftOnly) -> dashboardReportUseCase.findHourlyHeatmap(companyId,
                                arguments.optionalText(args, "from"), arguments.optionalText(args, "to"),
                                arguments.optionalId(tool, args, "campaignId"))),

                new McpToolDefinition("compare_campaigns", """
                        Campaigns side by side over a period: calls, answered, answer rate, average duration, \
                        promises. Omit campaignIds for every campaign.""", CALL_READ, """
                        {"type": "object", "properties": {%s,
                          "campaignIds": {"type": "array", "items": {"type": "integer"}, "maxItems": 20}}}""".formatted(PERIOD),
                        (companyId, tool, args, draftOnly) -> dashboardReportUseCase.findCampaignComparison(companyId,
                                arguments.optionalText(args, "from"), arguments.optionalText(args, "to"),
                                arguments.optionalIds(tool, args, "campaignIds"))),

                new McpToolDefinition("get_call_trace", """
                        One AI call block by block: speech recognition, model turns, tool calls, stage moves \
                        and their timings — WHY the agent was slow or did something.""", CALL_READ, """
                        {"type": "object", "required": ["callId"], "properties": {"callId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> contactCenterReportUseCase.findCallTrace(companyId,
                                arguments.requireId(tool, args, "callId"))),

                new McpToolDefinition("analyze_call", """
                        Deeply inspect one AI voice call: millisecond latency breakdown per turn \
                        (EOU, STT, LLM TTFT, chunk wait, TTS TTFB, total first_audio, speculation hits), \
                        dialogue flaws and quality verdict (guardrails, tool misuse, unanswered questions), \
                        tool calls, stage transitions, full transcript, and actionable critique.""", CALL_READ, """
                        {"type": "object", "required": ["callId"], "properties": {"callId": {"type": "integer"}}}""",
                        this::analyzeCall),

                new McpToolDefinition("list_agent_insights", """
                        The improvement proposals the daily job found in an agent's calls (with the calls \
                        behind them), and whether they were accepted or rejected.""", AGENT_READ, """
                        {"type": "object", "properties": {
                          "aiAgentId": {"type": "integer"},
                          "type": {"type": "string"},
                          "status": {"type": "string"},
                          "page": {"type": "integer", "minimum": 0},
                          "size": {"type": "integer", "minimum": 1, "maximum": 50}}}""",
                        (companyId, tool, args, draftOnly) -> agentInsightUseCase.filterInsights(companyId,
                                arguments.convert(tool, args, AgentInsightFilter.class))));
    }

    private Object findCallStats(long companyId, String tool, JsonNode args, boolean draftOnly) {
        String from = arguments.optionalText(args, "from");
        String to = arguments.optionalText(args, "to");
        Long campaignId = arguments.optionalId(tool, args, "campaignId");
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("summary", dashboardReportUseCase.findSummary(companyId, from, to, campaignId));
        answer.put("kpiAgainstPreviousPeriod", dashboardReportUseCase.findDashboardKpi(companyId, from, to, campaignId));
        return answer;
    }

    private CallPeriodComparison comparePeriods(long companyId, String tool, JsonNode args, boolean draftOnly) {
        Long campaignId = arguments.optionalId(tool, args, "campaignId");
        ReportSummary current = dashboardReportUseCase.findSummary(companyId, arguments.optionalText(args, "from"),
                arguments.optionalText(args, "to"), campaignId);
        String compareFrom = arguments.optionalText(args, "compareFrom");
        String compareTo = arguments.optionalText(args, "compareTo");
        if (compareFrom == null && compareTo == null) {
            Duration length = Duration.between(current.from(), current.to());
            Instant previousFrom = current.from().minus(length);
            compareFrom = previousFrom.toString();
            compareTo = current.from().toString();
        }
        ReportSummary previous = dashboardReportUseCase.findSummary(companyId, compareFrom, compareTo, campaignId);
        return CallPeriodComparison.of(current, previous);
    }

    private Object analyzeCall(long companyId, String tool, JsonNode args, boolean draftOnly) {
        long callId = arguments.requireId(tool, args, "callId");
        CallDetail detail = reportUseCase.findCallDetail(companyId, callId);

        CallQuality quality = null;
        try {
            quality = callReviewUseCase.findReview(companyId, callId);
        } catch (Exception ignored) {
            // Quality review may not be scored yet
        }

        List<CallEvent> events = callEventUseCase.findByCallAttemptId(companyId, callId);

        List<Map<String, Object>> turns = new ArrayList<>();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        List<Map<String, Object>> stageTransitions = new ArrayList<>();

        int turnIdx = 1;
        for (CallEvent event : events) {
            if (event.type() == CallEventType.TURN) {
                Map<String, Object> p = event.payload();
                Long utteranceEndMs = getLong(p, "utteranceEndMs");
                Long eouWaitMs = getLong(p, "eouWaitMs");
                Long sttFinalMs = getLong(p, "sttFinalMs");
                Long llmRequestMs = getLong(p, "llmRequestMs");
                Long llmFirstTokenMs = getLong(p, "llmFirstTokenMs");
                Long ttsRequestMs = getLong(p, "ttsRequestMs");
                Long firstAudioMs = getLong(p, "firstAudioMs");
                Boolean speculated = (Boolean) p.get("speculated");

                Long sttLatency = (sttFinalMs != null && utteranceEndMs != null) ? Math.max(0, sttFinalMs - utteranceEndMs) : null;
                Long llmTtft = (llmFirstTokenMs != null && llmRequestMs != null) ? Math.max(0, llmFirstTokenMs - llmRequestMs) : null;
                Long chunkWait = (ttsRequestMs != null && llmFirstTokenMs != null) ? Math.max(0, ttsRequestMs - llmFirstTokenMs) : null;
                Long ttsTtfb = (firstAudioMs != null && ttsRequestMs != null) ? Math.max(0, firstAudioMs - ttsRequestMs) : null;
                Long totalTurnLatency = (firstAudioMs != null && utteranceEndMs != null) ? Math.max(0, firstAudioMs - utteranceEndMs) : null;

                Map<String, Object> turnMap = new LinkedHashMap<>();
                turnMap.put("turn", turnIdx++);
                turnMap.put("speculated", Boolean.TRUE.equals(speculated));
                turnMap.put("eouWaitMs", eouWaitMs);
                turnMap.put("sttLatencyMs", sttLatency);
                turnMap.put("llmTtftMs", llmTtft);
                turnMap.put("chunkWaitMs", chunkWait);
                turnMap.put("ttsTtfbMs", ttsTtfb);
                turnMap.put("totalTurnLatencyMs", totalTurnLatency);
                turns.add(turnMap);
            } else if (event.type() == CallEventType.TOOL_CALLED) {
                toolCalls.add(event.payload());
            } else if (event.type() == CallEventType.STAGE_ENTERED) {
                stageTransitions.add(event.payload());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("callId", callId);
        result.put("call", detail.call());
        result.put("technical", detail.technical());
        result.put("talk", detail.talk());
        result.put("quality", quality);
        result.put("turnLatencies", turns);
        result.put("toolCalls", toolCalls);
        result.put("stageTransitions", stageTransitions);
        result.put("transcript", detail.transcript());
        return result;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
