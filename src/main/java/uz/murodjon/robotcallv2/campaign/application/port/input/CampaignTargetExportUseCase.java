package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.application.dto.TargetsExport;

public interface CampaignTargetExportUseCase {

    /**
     * Every target of the campaign with its status, attempts, outcome and converted flag, as CSV.
     *
     * @throws uz.murodjon.robotcallv2.shared.exception.NotFoundException   no such campaign for this company
     * @throws uz.murodjon.robotcallv2.shared.exception.ValidationException the campaign has more targets than one export carries
     */
    TargetsExport exportTargetsCsv(long companyId, long campaignId);
}
