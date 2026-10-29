package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.application.dto.BatchCallRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.BatchCallResponse;

public interface BatchCallUseCase {

    /** Creates a campaign for the agent, loads the CSV into it and starts it — all or nothing. */
    BatchCallResponse startBatchCall(long companyId, BatchCallRequest request);
}
