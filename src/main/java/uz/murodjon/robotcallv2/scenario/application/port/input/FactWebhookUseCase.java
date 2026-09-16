package uz.murodjon.robotcallv2.scenario.application.port.input;

import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhook;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;

/** A scenario's fact webhook, asked for this call's facts before the line carries any speech. */
public interface FactWebhookUseCase {

    /**
     * @return the endpoint's JSON body, or null when it could not be used for any reason —
     *         the call goes on with the facts it already had
     */
    String fetchFacts(FactWebhook webhook, FactWebhookRequest call);
}
