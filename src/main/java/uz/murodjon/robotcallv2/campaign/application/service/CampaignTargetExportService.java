package uz.murodjon.robotcallv2.campaign.application.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.campaign.application.dto.TargetsExport;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetExportUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetTableField;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Renders a campaign's targets as CSV for a person to open in a spreadsheet. The targets are
 * read in pages of the largest size the list endpoint allows, ordered by id so no row is
 * skipped or repeated while the pages are walked.
 */
@Service
public class CampaignTargetExportService implements CampaignTargetExportUseCase {

    /** A spreadsheet that big is not something a browser download should carry. */
    static final int MAX_ROWS = 100_000;

    /** A phone such as +998901234567 starts with a plus but is no formula; it is left as it is. */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[+-]?[0-9]{1,20}");

    private static final String HEADER =
            "id,phone,language,status,attempts,step,step_attempts,outcome,converted,do_not_call,next_attempt_at,score,context";

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;

    public CampaignTargetExportService(CampaignRepository campaignRepository,
                                       CampaignTargetRepository campaignTargetRepository) {
        this.campaignRepository = campaignRepository;
        this.campaignTargetRepository = campaignTargetRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TargetsExport exportTargetsCsv(long companyId, long campaignId) {
        if (campaignRepository.find(companyId, campaignId) == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, campaignId);
        }
        if (campaignTargetRepository.countByCampaign(companyId, campaignId) > MAX_ROWS) {
            throw new ValidationException(ErrorCode.CAMPAIGN_EXPORT_TOO_LARGE, MAX_ROWS);
        }

        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        LinkedHashMap<TargetTableField, Sort.Direction> byId = new LinkedHashMap<>();
        byId.put(TargetTableField.ID, Sort.Direction.ASC);
        for (int page = 0; ; page++) {
            List<CampaignTarget> rows = campaignTargetRepository.findByCampaign(companyId, campaignId,
                    new TargetFilter(page, FilterInterface.MAX_SIZE, byId));
            rows.forEach(target -> appendRow(csv, target));
            if (rows.size() < FilterInterface.MAX_SIZE) {
                break;
            }
        }
        // The BOM makes Excel read the file as UTF-8, so Uzbek and Russian text survives.
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        return new TargetsExport("campaign-" + campaignId + "-targets.csv", body);
    }

    private static void appendRow(StringBuilder csv, CampaignTarget target) {
        String[] cells = {
                String.valueOf(target.id()),
                target.phone(),
                target.language(),
                String.valueOf(target.status()),
                String.valueOf(target.attempts()),
                target.stepId(),
                String.valueOf(target.stepAttempts()),
                target.outcome(),
                String.valueOf(target.converted()),
                String.valueOf(target.doNotCall()),
                target.nextAttemptAt() != null ? target.nextAttemptAt().toString() : null,
                target.score() != null ? String.valueOf(target.score()) : null,
                target.contextData()
        };
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(cells[i]));
        }
        csv.append("\r\n");
    }

    /**
     * Quotes a cell, and defuses one a spreadsheet would run as a formula: a phone or a
     * context value came from an uploaded file, so a leading {@code =} must not execute.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean formulaLike = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 && !PLAIN_NUMBER.matcher(value).matches();
        String safe = formulaLike ? "'" + value : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
