package uz.murodjon.robotcallv2.audit.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.port.output.AuditLogRepository;
import uz.murodjon.robotcallv2.security.CurrentUser;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private static final long COMPANY = 7L;

    private AuditLogRepository repository;
    private AuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.actorName()).thenReturn("admin");
        when(currentUser.remoteAddress()).thenReturn(Optional.of("10.0.0.1"));
        service = new AuditService(repository, currentUser);
    }

    @Test
    void recordStampsWhoDidItAndFromWhere() {
        service.record(COMPANY, "CAMPAIGN_START", "campaign", "5", null);

        verify(repository).save(eq(COMPANY), argThat(log -> log.actor().equals("admin")
                && log.ipAddress().equals("10.0.0.1") && log.action().equals("CAMPAIGN_START")));
    }

    /** The audited action already happened — a failed audit write must not undo it. */
    @Test
    void aFailedWriteDoesNotFailTheAction() {
        when(repository.save(anyLong(), any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.record(COMPANY, "CAMPAIGN_START", "campaign", "5", null))
                .doesNotThrowAnyException();
    }

    @Test
    void aFailedReadShowsAnEmptyLog() {
        when(repository.findByCompanyId(eq(COMPANY), any())).thenThrow(new RuntimeException("db down"));
        when(repository.countByCompanyId(eq(COMPANY), any())).thenThrow(new RuntimeException("db down"));

        assertThat(service.findRecent(COMPANY, null)).isEmpty();
        assertThat(service.count(COMPANY, null)).isZero();
    }
}
