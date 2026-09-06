package uz.murodjon.robotcallv2.campaign.domain.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Sort;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignTableField;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.shared.api.FilterInterface;

import java.time.Instant;
import java.util.LinkedHashMap;

public record CampaignFilter(
        @Min(0) Integer page,
        @Min(1) @Max(FilterInterface.MAX_SIZE) Integer size,
        LinkedHashMap<CampaignTableField, Sort.Direction> orders,
        String search,
        CampaignStatus status,
        CampaignType type,
        Long aiAgentId,
        Long createdBy,
        RecurrenceType recurrenceType,
        Instant dateFrom,
        Instant dateTo)
        implements FilterInterface<CampaignTableField> {

    public CampaignFilter {
        if (orders == null || orders.isEmpty()) {
            orders = new LinkedHashMap<>();
            orders.put(CampaignTableField.CREATED_AT, Sort.Direction.DESC);
            orders.put(CampaignTableField.ID, Sort.Direction.DESC);
        } else if (!orders.containsKey(CampaignTableField.ID)) {
            LinkedHashMap<CampaignTableField, Sort.Direction> normalized = new LinkedHashMap<>(orders);
            normalized.put(CampaignTableField.ID, Sort.Direction.DESC);
            orders = normalized;
        }
    }

    public CampaignFilter(Integer page, Integer size, LinkedHashMap<CampaignTableField, Sort.Direction> orders, CampaignStatus status) {
        this(page, size, orders, null, status, null, null, null, null, null, null);
    }
}
