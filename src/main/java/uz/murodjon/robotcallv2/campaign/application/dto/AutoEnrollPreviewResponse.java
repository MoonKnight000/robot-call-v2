package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollCandidate;

import java.util.List;

/**
 * @param total  contacts the rules match, whether or not they are already in the campaign
 * @param sample the first few of them, oldest first
 */
public record AutoEnrollPreviewResponse(long total, List<AutoEnrollCandidate> sample) {
}
