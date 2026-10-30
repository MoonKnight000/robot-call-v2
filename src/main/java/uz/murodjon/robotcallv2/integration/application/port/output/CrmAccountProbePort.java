package uz.murodjon.robotcallv2.integration.application.port.output;

import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnectionCheck;

/**
 * What the integration feature needs from the CRM side: the platform's Uysot address, and
 * a way to ask an account whether its credential still works. Implemented by the CRM
 * connectors, which know each product's API.
 */
public interface CrmAccountProbePort {

    /** The platform's Uysot Open API address; {@code null} when the platform has none configured. */
    String findUysotBaseUrl();

    /** One cheap authenticated read against the account. Never throws: a failure is the answer. */
    CrmConnectionCheck probe(CrmConnection connection);
}
