package uz.murodjon.robotcallv2.crm.application.port.output;

import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.util.Map;

/**
 * Writes contact values back to the CRM record a contact was imported from. A CRM that is
 * down or refuses throws {@code ExternalServiceException}; the write is tried again later.
 */
public interface CrmContactWriterPort {

    boolean isSupported(CrmProvider provider);

    /**
     * Sets {@code values} (CRM field id → text) on the record {@code externalId}; fields not
     * listed are left as they are.
     */
    void updateContactFields(CrmConnection connection, String externalId, Map<String, String> values);
}
