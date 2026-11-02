package uz.murodjon.robotcallv2.sms.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.sms.application.dto.ConnectEskizRequest;
import uz.murodjon.robotcallv2.sms.application.dto.EskizConnectionRow;
import uz.murodjon.robotcallv2.sms.application.port.input.EskizConnectionUseCase;
import uz.murodjon.robotcallv2.sms.application.port.output.EskizConnectionRepository;
import uz.murodjon.robotcallv2.sms.application.port.output.SmsSenderPort;
import uz.murodjon.robotcallv2.sms.domain.entity.EskizConnection;

import java.time.Instant;

@Service
public class EskizConnectionService implements EskizConnectionUseCase {

    private static final int MAX_SENDER_LENGTH = 32;

    private final EskizConnectionRepository repository;
    private final SmsSenderPort smsSender;
    private final SecretCipher cipher;
    private final CompanyUseCase companyUseCase;
    private final AuditUseCase audit;

    public EskizConnectionService(EskizConnectionRepository repository, SmsSenderPort smsSender, SecretCipher cipher,
                                  CompanyUseCase companyUseCase, AuditUseCase audit) {
        this.repository = repository;
        this.smsSender = smsSender;
        this.cipher = cipher;
        this.companyUseCase = companyUseCase;
        this.audit = audit;
    }

    @Override
    public EskizConnectionRow findByCompanyId(long companyId) {
        return repository.findByCompanyId(companyId).map(EskizConnectionRow::of)
                .orElseGet(EskizConnectionRow::notConnected);
    }

    @Override
    public EskizConnectionRow connect(long companyId, ConnectEskizRequest request) {
        companyUseCase.requireIntegrationEnabled(companyId, IntegrationProvider.ESKIZ);
        String email = request.email() == null ? "" : request.email().strip();
        String password = request.password() == null ? "" : request.password();
        if (email.isEmpty() || password.isBlank()) {
            throw new ValidationException(ErrorCode.SMS_ESKIZ_CREDENTIALS_REQUIRED);
        }
        String sender = request.sender() == null || request.sender().isBlank() ? null : request.sender().strip();
        if (sender != null && sender.length() > MAX_SENDER_LENGTH) {
            throw new ValidationException(ErrorCode.SMS_ESKIZ_SENDER_INVALID);
        }
        if (!cipher.available()) {
            throw new ConflictException(ErrorCode.ENCRYPTION_KEY_NOT_SET);
        }
        if (!smsSender.verifyLogin(email, password)) {
            throw new ValidationException(ErrorCode.SMS_ESKIZ_LOGIN_FAILED);
        }
        EskizConnection saved = repository.upsert(companyId, new EskizConnection(email, password, sender, Instant.now()));
        audit.record(companyId, "ESKIZ_CONNECT", "eskiz_connection", String.valueOf(companyId), email);
        return EskizConnectionRow.of(saved);
    }

    @Override
    public void disconnect(long companyId) {
        repository.deleteByCompanyId(companyId);
        audit.record(companyId, "ESKIZ_DISCONNECT", "eskiz_connection", String.valueOf(companyId), null);
    }
}
