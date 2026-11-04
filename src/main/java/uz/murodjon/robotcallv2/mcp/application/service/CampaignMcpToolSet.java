package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignRow;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantCreateRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.CreateCampaignRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.UpdateCampaignRequest;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Campaigns for the copilot and MCP clients: read them, set one up as a DRAFT — who is called,
 * when, how often, from which numbers, with which agent — and load its contacts. There is no tool
 * that starts, pauses or stops a campaign: launching calls stays a person's button in the panel.
 *
 * <p>A drafts-only caller (the copilot always) changes only campaigns still in {@code DRAFT}:
 * a running campaign's settings, contacts and variants act on live dialing at once.
 */
@Component
@Order(20)
public class CampaignMcpToolSet implements McpToolSet {

    private static final Set<Permission> CAMPAIGN_READ = EnumSet.of(Permission.CAMPAIGN_READ, Permission.CAMPAIGN_EDIT);
    private static final Set<Permission> CAMPAIGN_WRITE = EnumSet.of(Permission.CAMPAIGN_EDIT);

    /** The fields of the REST update request; a CampaignRow carries them under the same names. */
    private static final List<String> UPDATE_FIELDS = List.of("name", "aiAgentId", "priority", "schedule", "retry",
            "limits", "recurrence", "dialing", "numbers", "sequence", "autoEnroll", "version", "autoRetries");

    private static final String CAMPAIGN_FIELDS = """
            "name": {"type": "string"},
            "aiAgentId": {"type": "integer", "description": "The agent whose voice and scenario the calls run under (list_agents); required for dialing.mode AI"},
            "priority": {"type": "integer"},
            "schedule": {"type": "object", "description": "startAt, endAt (ISO instants), dialWindowStart, dialWindowEnd (HH:mm, company's zone), dialDays ([MONDAY, ...]), dialRules"},
            "retry": {"type": "object", "description": "maxAttempts, intervalMinutes, policy ({disposition: rule})"},
            "limits": {"type": "object", "description": "maxConcurrentCalls, dailyCallCap, budgetUzs, dailyBudgetUzs, maxCps"},
            "recurrence": {"type": "object", "description": "type (ONCE, DAILY, WEEKLY, MONTHLY, CRON), dayOfMonth, cronExpression, autoResetTargets"},
            "dialing": {"type": "object", "description": "mode (AI, PREVIEW, PROGRESSIVE, PREDICTIVE), callQueueId, maxAiOverflowPercent, targetOrdering"},
            "numbers": {"type": "object", "description": "callerIdPoolId, localPresence, fromNumberId — which numbers the calls go out from"},
            "sequence": {"type": "object", "description": "Steps: which agent, how many attempts, next step by outcome"},
            "autoEnroll": {"type": "object", "description": "Rules that add contacts on their own (kind AUTO_ENROLL)"},
            "autoRetries": {"type": "object"}""";

    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final CampaignVariantUseCase campaignVariantUseCase;
    private final McpToolArguments arguments;

    public CampaignMcpToolSet(CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                              CampaignVariantUseCase campaignVariantUseCase, Validator validator) {
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.campaignVariantUseCase = campaignVariantUseCase;
        this.arguments = new McpToolArguments(validator);
    }

    @Override
    public List<McpToolDefinition> findTools() {
        return List.of(
                new McpToolDefinition("list_campaigns", "The company's campaigns, newest first, with status and contact counts.",
                        CAMPAIGN_READ, """
                        {"type": "object", "properties": {
                          "search": {"type": "string"},
                          "status": {"type": "string", "enum": ["DRAFT", "SCHEDULED", "ACTIVE", "PAUSED", "COMPLETED", "ARCHIVED"]},
                          "aiAgentId": {"type": "integer"},
                          "page": {"type": "integer", "minimum": 0},
                          "size": {"type": "integer", "minimum": 1, "maximum": 100}}}""",
                        (companyId, tool, args, draftOnly) -> campaignUseCase.filterCampaigns(companyId,
                                arguments.convert(tool, args, CampaignFilter.class))),

                new McpToolDefinition("get_campaign", """
                        One campaign in full: schedule, retries, limits, dialing, numbers, sequence, contact \
                        counts by status and what still blocks its launch (launchBlocker).""", CAMPAIGN_READ, """
                        {"type": "object", "required": ["campaignId"], "properties": {"campaignId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> campaignUseCase.requireCampaignOverview(companyId,
                                arguments.requireId(tool, args, "campaignId"))),

                new McpToolDefinition("create_campaign_draft", """
                        Create a campaign. It is always a DRAFT: nobody is called until a person starts it in \
                        the panel. Only who is called and when — the voice, scenario and model come from aiAgentId.""",
                        CAMPAIGN_WRITE, """
                        {"type": "object", "required": ["name"], "properties": {%s,
                          "kind": {"type": "string", "enum": ["STANDARD", "AUTO_ENROLL"]}}}""".formatted(CAMPAIGN_FIELDS),
                        (companyId, tool, args, draftOnly) -> campaignUseCase.createCampaign(companyId,
                                arguments.convert(tool, args, CreateCampaignRequest.class))),

                new McpToolDefinition("update_campaign_draft", """
                        Change a campaign; omitted fields keep their value and a group (schedule, retry, ...) \
                        is merged field by field. Only a DRAFT campaign for drafts-only callers.""",
                        CAMPAIGN_WRITE, """
                        {"type": "object", "required": ["campaignId"], "properties": {"campaignId": {"type": "integer"}, %s}}"""
                                .formatted(CAMPAIGN_FIELDS),
                        this::updateCampaign),

                new McpToolDefinition("preview_campaign_targets", """
                        Check a contacts CSV before loading it: detected columns, sample rows, problems. \
                        Loads nothing.""", CAMPAIGN_WRITE, """
                        {"type": "object", "required": ["campaignId", "csv"], "properties": {
                          "campaignId": {"type": "integer"},
                          "csv": {"type": "string", "description": "CSV text with a header: phone (required), clientId, clientName, language and any context columns"}}}""",
                        (companyId, tool, args, draftOnly) -> campaignTargetUseCase.previewTargetsCsv(companyId,
                                arguments.requireId(tool, args, "campaignId"), arguments.requireText(tool, args, "csv"))),

                new McpToolDefinition("import_campaign_targets", """
                        Load contacts from CSV text into a campaign (do-not-call and duplicate numbers are \
                        skipped and reported). Only a DRAFT campaign for drafts-only callers.""", CAMPAIGN_WRITE, """
                        {"type": "object", "required": ["campaignId", "csv"], "properties": {
                          "campaignId": {"type": "integer"},
                          "csv": {"type": "string"}}}""",
                        (companyId, tool, args, draftOnly) -> {
                            long campaignId = arguments.requireId(tool, args, "campaignId");
                            requireDraftWhenDraftOnly(companyId, campaignId, draftOnly);
                            return campaignTargetUseCase.importTargetsCsv(companyId, campaignId, arguments.requireText(tool, args, "csv"));
                        }),

                new McpToolDefinition("list_campaign_variants", "A campaign's A/B variants and their traffic weights.",
                        CAMPAIGN_READ, """
                        {"type": "object", "required": ["campaignId"], "properties": {"campaignId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> campaignVariantUseCase.list(companyId,
                                arguments.requireId(tool, args, "campaignId"))),

                new McpToolDefinition("get_campaign_ab_report", "A/B result per variant: calls, answers, conversions and rates.",
                        CAMPAIGN_READ, """
                        {"type": "object", "required": ["campaignId"], "properties": {"campaignId": {"type": "integer"}}}""",
                        (companyId, tool, args, draftOnly) -> campaignVariantUseCase.findAbTestReport(companyId,
                                arguments.requireId(tool, args, "campaignId"))),

                new McpToolDefinition("create_campaign_variant", """
                        Add an A/B variant: another agent (aiAgentId), another role prompt (promptOverride), \
                        or another voice / pipeline to compare. Only a DRAFT campaign for drafts-only callers.""",
                        CAMPAIGN_WRITE, """
                        {"type": "object", "required": ["campaignId", "name"], "properties": {
                          "campaignId": {"type": "integer"},
                          "name": {"type": "string", "maxLength": 100},
                          "aiAgentId": {"type": "integer"},
                          "promptOverride": {"type": "string"},
                          "ttsVoiceId": {"type": "string"},
                          "pipelineMode": {"type": "string"},
                          "trafficWeight": {"type": "integer", "minimum": 1, "maximum": 100}}}""",
                        (companyId, tool, args, draftOnly) -> {
                            long campaignId = arguments.requireId(tool, args, "campaignId");
                            requireDraftWhenDraftOnly(companyId, campaignId, draftOnly);
                            ObjectNode body = args.deepCopy();
                            body.remove("campaignId");
                            return campaignVariantUseCase.create(companyId, campaignId,
                                    arguments.convert(tool, body, CampaignVariantCreateRequest.class));
                        }));
    }

    private CampaignRow updateCampaign(long companyId, String tool, ObjectNode args, boolean draftOnly) {
        long campaignId = arguments.requireId(tool, args, "campaignId");
        CampaignRow current = requireDraftWhenDraftOnly(companyId, campaignId, draftOnly);
        ObjectNode currentFields = arguments.mapper().valueToTree(current);
        ObjectNode body = arguments.mapper().createObjectNode();
        for (String field : UPDATE_FIELDS) {
            if (currentFields.has(field)) {
                body.set(field, currentFields.get(field));
            }
        }
        ObjectNode patch = args.deepCopy();
        patch.remove("campaignId");
        arguments.merge(body, patch);
        return campaignUseCase.updateCampaign(companyId, campaignId, arguments.convert(tool, body, UpdateCampaignRequest.class));
    }

    private CampaignRow requireDraftWhenDraftOnly(long companyId, long campaignId, boolean draftOnly) {
        CampaignRow current = campaignUseCase.requireCampaignRow(companyId, campaignId);
        if (draftOnly && current.status() != CampaignStatus.DRAFT) {
            throw new ForbiddenException(ErrorCode.MCP_CAMPAIGN_NOT_DRAFT, campaignId, current.status());
        }
        return current;
    }
}
