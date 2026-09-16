package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.auth.application.dto.TwoFactorSetupResponse;
import uz.murodjon.robotcallv2.auth.application.port.input.TwoFactorUseCase;
import uz.murodjon.robotcallv2.auth.application.port.output.UserTotpRepository;
import uz.murodjon.robotcallv2.auth.domain.entity.UserTotp;
import uz.murodjon.robotcallv2.auth.domain.service.Totp;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;

import java.time.Clock;

/**
 * Two-factor login with an authenticator app (NEXT-PLAN S.3). The secret is stored
 * encrypted with the secret-vault key, so a database dump alone does not yield codes; a
 * server without that key cannot offer two-factor login at all.
 */
@Service
public class TwoFactorService implements TwoFactorUseCase {

    /** The name an authenticator app lists the account under. */
    private static final String ISSUER = "RobotCall";

    private final UserTotpRepository repository;
    private final SecretCipher cipher;
    private final AuditUseCase audit;
    private final Clock clock;

    public TwoFactorService(UserTotpRepository repository, SecretCipher cipher, AuditUseCase audit, Clock clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public TwoFactorSetupResponse startTwoFactorSetup(long companyId, long userId, String accountName) {
        UserTotp existing = repository.find(userId);
        if (existing != null && existing.enabled()) {
            throw new ConflictException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        if (!cipher.available()) {
            throw new ConflictException(ErrorCode.TOTP_UNAVAILABLE);
        }
        // Setting up again before enabling replaces the unproven secret.
        String secret = Totp.newSecret();
        repository.save(UserTotp.pending(userId, companyId, cipher.encrypt(secret)));
        return new TwoFactorSetupResponse(secret, Totp.otpauthUri(ISSUER, accountName, secret));
    }

    @Override
    public void enable(long companyId, long userId, String code) {
        UserTotp totp = repository.find(userId);
        if (totp == null) {
            throw new ConflictException(ErrorCode.TOTP_NOT_SET_UP);
        }
        if (totp.enabled()) {
            throw new ConflictException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        long step = Totp.matchingStep(cipher.decrypt(totp.secretEncrypted()), code, clock.instant(),
                totp.lastUsedStep());
        if (step < 0) {
            throw new ValidationException(ErrorCode.TOTP_CODE_INVALID);
        }
        repository.save(totp.activate(step, clock.instant()));
        audit.record(companyId, "TOTP_ENABLED", "user", String.valueOf(userId), null);
    }

    @Override
    public void disable(long companyId, long userId, String code) {
        UserTotp totp = repository.find(userId);
        if (totp == null || !totp.enabled()) {
            throw new ConflictException(ErrorCode.TOTP_NOT_SET_UP);
        }
        if (!verify(totp, code)) {
            throw new ValidationException(ErrorCode.TOTP_CODE_INVALID);
        }
        repository.delete(userId);
        audit.record(companyId, "TOTP_DISABLED", "user", String.valueOf(userId), null);
    }

    @Override
    public boolean isEnabled(long userId) {
        UserTotp totp = repository.find(userId);
        return totp != null && totp.enabled();
    }

    @Override
    public boolean verifyCode(long userId, String code) {
        UserTotp totp = repository.find(userId);
        return totp != null && totp.enabled() && verify(totp, code);
    }

    /** A code counts once: the step it matched is claimed atomically, so a replay fails. */
    private boolean verify(UserTotp totp, String code) {
        long step = Totp.matchingStep(cipher.decrypt(totp.secretEncrypted()), code, clock.instant(),
                totp.lastUsedStep());
        return step >= 0 && repository.markUsed(totp.userId(), step);
    }
}
