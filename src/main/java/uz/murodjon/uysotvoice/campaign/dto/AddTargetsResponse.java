package uz.murodjon.uysotvoice.campaign.dto;

import java.util.List;

public record AddTargetsResponse(long campaignId, int added, List<Long> targetIds) {
}
