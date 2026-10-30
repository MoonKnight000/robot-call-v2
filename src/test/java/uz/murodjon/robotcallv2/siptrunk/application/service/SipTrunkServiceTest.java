package uz.murodjon.robotcallv2.siptrunk.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.murodjon.robotcallv2.agent.ami.AmiClient;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.SipTrunkRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkCredentials;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** {@link SipTrunkService#requireCredentialsForCall} — what the LiveKit engine dials a company trunk with. */
@ExtendWith(MockitoExtension.class)
class SipTrunkServiceTest {

    private static final long COMPANY_ID = 7L;

    @Mock
    private SipTrunkRepository repository;
    @Mock
    private PjsipConfigWriter pjsipConfigWriter;
    @Mock
    private SecretCipher cipher;
    @Mock
    private AuditUseCase audit;
    @Mock
    private AmiClient ami;
    @Mock
    private CurrentUser currentUser;

    private SipTrunkService service;

    @BeforeEach
    void setUp() {
        service = new SipTrunkService(repository, pjsipConfigWriter, cipher, audit, ami, currentUser);
    }

    @Test
    void managedTrunkComesBackWithItsPasswordDecrypted() {
        when(repository.find(COMPANY_ID, 3L)).thenReturn(trunk(3L, "sip.provider.uz", "enc"));
        when(cipher.decrypt("enc")).thenReturn("s3cret");

        SipTrunkCredentials credentials = service.requireCredentialsForCall(COMPANY_ID, 3L);

        assertThat(credentials.host()).isEqualTo("sip.provider.uz");
        assertThat(credentials.port()).isEqualTo(5060);
        assertThat(credentials.username()).isEqualTo("login");
        assertThat(credentials.password()).isEqualTo("s3cret");
        assertThat(credentials.transport()).isEqualTo(SipTrunkTransport.UDP);
        assertThat(credentials.toString()).doesNotContain("s3cret");
    }

    @Test
    void manualPjsipEndpointIsRefused() {
        when(repository.find(COMPANY_ID, 4L)).thenReturn(trunk(4L, null, null));

        assertThatThrownBy(() -> service.requireCredentialsForCall(COMPANY_ID, 4L))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SIP_TRUNK_NOT_MANAGED);
    }

    @Test
    void anotherCompanysTrunkIsNotFound() {
        when(repository.find(COMPANY_ID, 5L)).thenReturn(null);

        assertThatThrownBy(() -> service.requireCredentialsForCall(COMPANY_ID, 5L))
                .isInstanceOf(NotFoundException.class);
    }

    private static SipTrunk trunk(long id, String host, String passwordEnc) {
        return new SipTrunk(id, COMPANY_ID, "main", "trunk-" + id, "998712001122", host, 5060,
                host != null ? "login" : null, passwordEnc, SipTrunkTransport.UDP, List.of("ulaw"),
                null, null, true, true, Instant.EPOCH);
    }
}
