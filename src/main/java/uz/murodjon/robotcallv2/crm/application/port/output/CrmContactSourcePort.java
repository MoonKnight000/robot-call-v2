package uz.murodjon.robotcallv2.crm.application.port.output;

import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactPage;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmFieldOption;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A CRM's contacts, read for the contact sync. Unlike {@link CrmConnectorPort}, a refusal is
 * an answer here: a CRM that is down or refuses throws {@code ExternalServiceException}, and
 * the sync records why.
 */
public interface CrmContactSourcePort {

    boolean isSupported(CrmProvider provider);

    /**
     * One page of contacts changed at or after {@code since} ({@code null} — every contact),
     * oldest change first, each with the {@code fields} it has a value for.
     *
     * @param pageToken {@code null} for the first page, else the previous page's {@code nextPageToken}
     */
    CrmContactPage findContactsModifiedSince(CrmConnection connection, Instant since, String pageToken,
                                             Set<String> fields);

    /** The contact fields the CRM has, for the mapping screen. */
    List<CrmFieldOption> findContactFields(CrmConnection connection);
}
