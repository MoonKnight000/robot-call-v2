package uz.murodjon.robotcallv2.crm.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactCrmImportUseCase;
import uz.murodjon.robotcallv2.contact.application.port.input.ContactFieldUseCase;
import uz.murodjon.robotcallv2.contact.domain.entity.ContactField;
import uz.murodjon.robotcallv2.contact.domain.entity.ImportedCrmContact;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactImportOutcome;
import uz.murodjon.robotcallv2.crm.application.dto.CrmContactSyncResponse;
import uz.murodjon.robotcallv2.crm.application.dto.UpdateCrmContactSyncRequest;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmContactSyncUseCase;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactSourcePort;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactSyncRepository;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactFieldMapping;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactPage;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactRecord;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncCounts;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncResume;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncSettings;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncState;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmFieldOption;
import uz.murodjon.robotcallv2.crm.domain.enums.CrmContactSyncStatus;
import uz.murodjon.robotcallv2.crm.domain.service.CrmContactSyncValidator;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Imports a company's CRM contacts into its contacts, every {@link #INTERVAL} from where the
 * last run stopped (Retell's contact sync, CRM → platform). Never deletes a contact.
 */
@Service
public class CrmContactSyncService implements CrmContactSyncUseCase {

    private static final Logger log = LoggerFactory.getLogger(CrmContactSyncService.class);

    static final Duration INTERVAL = Duration.ofMinutes(5);
    /** A safety stop for one run; progress is saved after every page, so the next run goes on. */
    private static final int MAX_RECORDS_PER_RUN = 100_000;
    /**
     * How long one company may hold the scheduler; a pass still open then goes on at the next
     * tick. Keeps one slow CRM from delaying every other company's sync.
     */
    static final Duration RUN_TIME_BUDGET = Duration.ofMinutes(3);
    private static final int MAX_ERROR_LENGTH = 500;

    private final CrmContactSyncRepository crmContactSyncRepository;
    private final List<CrmContactSourcePort> contactSources;
    private final CrmConnectionResolver crmConnectionResolver;
    private final ContactCrmImportUseCase contactCrmImportUseCase;
    private final ContactFieldUseCase contactFieldUseCase;

    public CrmContactSyncService(CrmContactSyncRepository crmContactSyncRepository, List<CrmContactSourcePort> contactSources,
                          CrmConnectionResolver crmConnectionResolver, ContactCrmImportUseCase contactCrmImportUseCase,
                          ContactFieldUseCase contactFieldUseCase) {
        this.crmContactSyncRepository = crmContactSyncRepository;
        this.contactSources = contactSources;
        this.crmConnectionResolver = crmConnectionResolver;
        this.contactCrmImportUseCase = contactCrmImportUseCase;
        this.contactFieldUseCase = contactFieldUseCase;
    }

    @Override
    public CrmContactSyncResponse findContactSync(long companyId) {
        CrmContactSyncSettings settings = crmContactSyncRepository.findSettings(companyId);
        CrmConnection connection = crmConnectionResolver.findConnection(companyId);
        return new CrmContactSyncResponse(
                settings != null && settings.enabled(),
                connection != null ? connection.provider().name() : null,
                connection != null && findSource(connection) != null,
                settings != null ? settings.fieldMappings() : List.of(),
                settings != null ? settings.pushMappings() : List.of(),
                crmContactSyncRepository.findState(companyId));
    }

    @Override
    public CrmContactSyncResponse updateContactSync(long companyId, UpdateCrmContactSyncRequest request) {
        List<CrmContactFieldMapping> mappings = request.fieldMappings() == null ? List.of() : request.fieldMappings();
        Set<String> contactFieldKeys = contactFieldUseCase.findAllByCompanyId(companyId).stream()
                .map(ContactField::key).collect(Collectors.toSet());
        CrmContactSyncValidator.validateMappings(mappings, contactFieldKeys);
        List<CrmContactFieldMapping> pushMappings = request.pushMappings() == null ? List.of() : request.pushMappings();
        CrmContactSyncValidator.validatePushMappings(pushMappings, contactFieldKeys);
        CrmContactSyncSettings before = crmContactSyncRepository.findSettings(companyId);
        boolean enabled = request.enabled();
        crmContactSyncRepository.saveSettings(companyId, CrmContactSyncSettings.values(enabled, mappings, pushMappings));
        if (enabled && (before == null || !before.enabled())) {
            scheduleNow(companyId, false);
        }
        return findContactSync(companyId);
    }

    @Override
    public List<CrmFieldOption> findCrmContactFields(long companyId) {
        CrmConnection connection = requireConnection(companyId);
        return requireSource(connection).findContactFields(connection);
    }

    @Override
    public CrmContactSyncResponse requestContactSyncRun(long companyId, boolean full) {
        CrmContactSyncSettings settings = crmContactSyncRepository.findSettings(companyId);
        if (settings == null || !settings.enabled()) {
            throw new ConflictException(ErrorCode.CRM_CONTACT_SYNC_DISABLED);
        }
        requireSource(requireConnection(companyId));
        scheduleNow(companyId, full);
        return findContactSync(companyId);
    }

    /** Runs every due company's sync, one after the other; called by the scheduler. */
    void runDueContactSyncs(int limit) {
        for (long companyId : crmContactSyncRepository.findDueCompanyIds(Instant.now(), limit)) {
            try {
                runContactSync(companyId);
            } catch (Exception e) {
                log.error("Contact sync of company {} crashed: {}", companyId, e.getMessage(), e);
            }
        }
    }

    /**
     * One run: the CRM contacts changed since the cursor, page by page. A pass that does not
     * finish within {@link #RUN_TIME_BUDGET} (a slow, rate-limited CRM such as Uysot) stops
     * after a page and the next run goes on from that page; progress is saved after every page.
     */
    void runContactSync(long companyId) {
        CrmContactSyncSettings settings = crmContactSyncRepository.findSettings(companyId);
        if (settings == null || !settings.enabled()) {
            return;
        }
        CrmContactSyncState state = crmContactSyncRepository.findState(companyId);
        CrmConnection connection = crmConnectionResolver.findConnection(companyId);
        if (connection == null) {
            finish(companyId, state.provider(), state.cursor(), state.resume(), CrmContactSyncStatus.NOT_CONNECTED,
                    null, CrmContactSyncCounts.NONE);
            return;
        }
        String provider = connection.provider().name();
        CrmContactSourcePort source = findSource(connection);
        if (source == null) {
            finish(companyId, provider, null, null, CrmContactSyncStatus.UNSUPPORTED, null, CrmContactSyncCounts.NONE);
            return;
        }
        // The cursor is the old CRM's time line: a switched CRM is read in full.
        boolean sameCrm = provider.equals(state.provider());
        Instant cursor = sameCrm ? state.cursor() : null;
        CrmContactSyncResume open = sameCrm ? state.resume() : null;
        Instant since = open != null ? open.since() : cursor;
        String pageToken = open != null ? open.pageToken() : null;
        Set<String> crmFields = settings.fieldMappings().stream().map(CrmContactFieldMapping::crmField)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int read = 0;
        Instant deadline = Instant.now().plus(RUN_TIME_BUDGET);
        try {
            do {
                CrmContactPage page = source.findContactsModifiedSince(connection, since, pageToken, crmFields);
                for (CrmContactRecord record : page.records()) {
                    switch (importRecord(companyId, provider, record, settings.fieldMappings())) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case SKIPPED -> skipped++;
                        case UNCHANGED -> {
                        }
                    }
                    if (record.modifiedAt() != null && (cursor == null || record.modifiedAt().isAfter(cursor))) {
                        cursor = record.modifiedAt();
                    }
                }
                read += page.records().size();
                pageToken = page.nextPageToken();
                // Saved as it goes: a run cut short by a restart resumes from here, not from the start.
                crmContactSyncRepository.saveState(companyId, new CrmContactSyncState(provider, cursor,
                        resumeAt(since, pageToken), state.nextRunAt(), state.lastRunAt(), state.lastStatus(),
                        state.lastError(), state.lastCounts()));
            } while (pageToken != null && read < MAX_RECORDS_PER_RUN && Instant.now().isBefore(deadline));
            CrmContactSyncCounts counts = new CrmContactSyncCounts(created, updated, skipped);
            finish(companyId, provider, cursor, resumeAt(since, pageToken), CrmContactSyncStatus.SUCCESS, null, counts);
            log.info("Contact sync of company {} from {}: {} read, {} created, {} updated, {} skipped{}",
                    companyId, provider, read, created, updated, skipped, pageToken != null ? ", more to come" : "");
        } catch (Exception e) {
            // An AppException's message is ours to show; anything else is a bug, named by its type only.
            String error = e instanceof AppException ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("Contact sync of company {} from {} failed: {}", companyId, provider, error, e);
            // The page that failed is read again next time: the pass stays open where it was.
            finish(companyId, provider, cursor, resumeAt(since, pageToken), CrmContactSyncStatus.FAILED, error,
                    new CrmContactSyncCounts(created, updated, skipped));
        }
    }

    private static CrmContactSyncResume resumeAt(Instant since, String pageToken) {
        return pageToken != null ? new CrmContactSyncResume(since, pageToken) : null;
    }

    private ContactImportOutcome importRecord(long companyId, String provider, CrmContactRecord record,
                                              List<CrmContactFieldMapping> mappings) {
        String phone = record.phones().stream().map(PhoneNumbers::normalize).filter(p -> p != null)
                .findFirst().orElse(null);
        if (phone == null) {
            return ContactImportOutcome.SKIPPED;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (CrmContactFieldMapping mapping : mappings) {
            String value = record.fields().get(mapping.crmField());
            if (value != null) {
                values.put(mapping.contactField(), value);
            }
        }
        try {
            return contactCrmImportUseCase.importCrmContact(companyId,
                    new ImportedCrmContact(provider, record.externalId(), record.name(), phone, values));
        } catch (Exception e) {
            log.warn("Contact sync of company {}: CRM record {} not imported: {}", companyId, record.externalId(),
                    e.getMessage());
            return ContactImportOutcome.SKIPPED;
        }
    }

    /**
     * Ends a run. A pass left open after a successful run continues at the next tick; anything
     * else — a finished pass, or a failure — waits for {@link #INTERVAL}.
     */
    private void finish(long companyId, String provider, Instant cursor, CrmContactSyncResume resume,
                        CrmContactSyncStatus status, String error, CrmContactSyncCounts counts) {
        Instant now = Instant.now();
        boolean more = resume != null && status == CrmContactSyncStatus.SUCCESS;
        String trimmedError = error != null && error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        crmContactSyncRepository.saveState(companyId, new CrmContactSyncState(provider, cursor, resume,
                more ? now : now.plus(INTERVAL), now, status, trimmedError, counts));
    }

    private void scheduleNow(long companyId, boolean full) {
        CrmContactSyncState state = crmContactSyncRepository.findState(companyId);
        crmContactSyncRepository.saveState(companyId, new CrmContactSyncState(state.provider(),
                full ? null : state.cursor(), full ? null : state.resume(), Instant.now(), state.lastRunAt(),
                state.lastStatus(), state.lastError(), state.lastCounts()));
    }

    private CrmContactSourcePort findSource(CrmConnection connection) {
        return contactSources.stream().filter(source -> source.isSupported(connection.provider()))
                .findFirst().orElse(null);
    }

    private CrmConnection requireConnection(long companyId) {
        CrmConnection connection = crmConnectionResolver.findConnection(companyId);
        if (connection == null) {
            throw new ConflictException(ErrorCode.CRM_CONTACT_SYNC_NOT_CONNECTED);
        }
        return connection;
    }

    private CrmContactSourcePort requireSource(CrmConnection connection) {
        CrmContactSourcePort source = findSource(connection);
        if (source == null) {
            throw new ConflictException(ErrorCode.CRM_CONTACT_SYNC_UNSUPPORTED, connection.provider());
        }
        return source;
    }
}
