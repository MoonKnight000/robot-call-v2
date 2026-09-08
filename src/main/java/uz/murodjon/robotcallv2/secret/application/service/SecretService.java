package uz.murodjon.robotcallv2.secret.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.secret.application.dto.CreateSecretRequest;
import uz.murodjon.robotcallv2.secret.application.dto.SecretRow;
import uz.murodjon.robotcallv2.secret.application.dto.UpdateSecretRequest;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.secret.application.port.output.SecretRepository;
import uz.murodjon.robotcallv2.secret.domain.entity.Secret;
import uz.murodjon.robotcallv2.secret.domain.service.SecretPlaceholders;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The company's API keys and tokens, encrypted at rest and never returned in full.
 *
 * <p>A value leaves this service in the clear in exactly one place — {@link
 * #findDecryptedSecrets} — and only so a tool or webhook call can be built with it inside
 * the process. Everything the HTTP layer can reach comes back masked.
 */
@Service
public class SecretService implements SecretUseCase {

    private static final Logger log = LoggerFactory.getLogger(SecretService.class);

    private final SecretRepository secretRepository;
    private final SecretCipher secretCipher;

    public SecretService(SecretRepository secretRepository, SecretCipher secretCipher) {
        this.secretRepository = secretRepository;
        this.secretCipher = secretCipher;
    }

    @Override
    @Transactional
    public SecretRow createSecret(long companyId, CreateSecretRequest request) {
        String key = request.key().trim();
        if (secretRepository.existsByCompanyIdAndKey(companyId, key)) {
            throw new ConflictException(ErrorCode.SECRET_KEY_EXISTS, key);
        }

        String plaintext = request.value().trim();
        Secret saved = secretRepository.save(new Secret(
                null,
                companyId,
                key,
                encrypt(plaintext),
                request.description(),
                Instant.now(),
                Instant.now()
        ));
        log.info("[Company {}] Created secret '{}'", companyId, key);
        return toRow(saved, plaintext);
    }

    @Override
    @Transactional
    public SecretRow updateSecret(long companyId, long id, UpdateSecretRequest request) {
        Secret existing = requireSecret(companyId, id);

        // A blank value means "leave the secret alone" — the screen never had the
        // plaintext to send back, so demanding it would make editing the description
        // impossible. The key itself is fixed once created; anything referencing
        // {{secrets.KEY}} would otherwise break silently on a rename.
        boolean replacingValue = request.value() != null && !request.value().isBlank();
        String plaintext = replacingValue ? request.value().trim() : decrypt(existing.value());
        String storedValue = replacingValue ? encrypt(plaintext) : existing.value();

        Secret saved = secretRepository.save(new Secret(
                existing.id(),
                existing.companyId(),
                existing.key(),
                storedValue,
                request.description() != null ? request.description() : existing.description(),
                existing.createdAt(),
                Instant.now()
        ));
        log.info("[Company {}] Updated secret '{}' (id={})", companyId, existing.key(), id);
        return toRow(saved, plaintext);
    }

    @Override
    @Transactional(readOnly = true)
    public SecretRow findSecret(long companyId, long id) {
        Secret existing = requireSecret(companyId, id);
        return toRow(existing, decrypt(existing.value()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecretRow> findSecrets(long companyId) {
        return secretRepository.findByCompanyId(companyId).stream()
                .map(secret -> toRow(secret, decrypt(secret.value())))
                .toList();
    }

    @Override
    @Transactional
    public void deleteSecret(long companyId, long id) {
        Secret existing = requireSecret(companyId, id);
        secretRepository.deleteByCompanyIdAndId(companyId, id);
        log.info("[Company {}] Deleted secret '{}' (id={})", companyId, existing.key(), id);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveSecrets(long companyId, String text) {
        if (text == null || !text.contains("{{")) {
            return text;
        }
        return SecretPlaceholders.resolve(text, findDecryptedSecrets(companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> findDecryptedSecrets(long companyId) {
        Map<String, String> decrypted = new LinkedHashMap<>();
        for (Secret secret : secretRepository.findByCompanyId(companyId)) {
            String value = decrypt(secret.value());
            if (value != null) {
                decrypted.put(secret.key(), value);
            }
        }
        return decrypted;
    }

    private Secret requireSecret(long companyId, long id) {
        return secretRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SECRET_NOT_FOUND, id));
    }

    /**
     * Refuses to store rather than storing in the clear: a vault that silently keeps
     * plaintext when the key is missing is worse than one that will not accept the value,
     * because nothing afterwards says which of the two happened.
     */
    private String encrypt(String plaintext) {
        if (!secretCipher.available()) {
            throw new ExternalServiceException(ErrorCode.ENCRYPTION_KEY_NOT_SET, "secret-vault");
        }
        return secretCipher.encrypt(plaintext);
    }

    /**
     * {@code null} when the value cannot be read back — a key rotated out from under the
     * rows, or a corrupted ciphertext. Callers mask it or leave the placeholder standing;
     * what must not happen is the ciphertext being handed on as if it were the secret.
     */
    private String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank() || !secretCipher.available()) {
            return null;
        }
        try {
            return secretCipher.decrypt(encrypted);
        } catch (Exception e) {
            log.warn("Secret could not be decrypted: {}", e.getMessage());
            return null;
        }
    }

    private SecretRow toRow(Secret secret, String plaintext) {
        return new SecretRow(
                secret.id(),
                secret.companyId(),
                secret.key(),
                SecretRow.mask(plaintext),
                secret.description(),
                secret.createdAt(),
                secret.updatedAt()
        );
    }
}
