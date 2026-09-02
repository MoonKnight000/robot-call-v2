package uz.murodjon.robotcallv2.campaign.application.dto;

import java.util.List;

public record AddTargetsResponse(long campaignId, int added, List<Long> targetIds) {
}
