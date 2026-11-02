package uz.murodjon.robotcallv2.sms.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.sms.domain.entity.EskizConnection;
import uz.murodjon.robotcallv2.sms.infrastructure.persistence.entity.EskizConnectionEntity;

import java.time.Instant;

/** Encrypts the login on its way in and decrypts it on its way out: nothing else ever sees it stored. */
@Component
public class EskizConnectionEntityMapper {

    private final SecretCipher cipher;

    public EskizConnectionEntityMapper(SecretCipher cipher) {
        this.cipher = cipher;
    }

    public EskizConnection toEskizConnection(EskizConnectionEntity entity) {
        return new EskizConnection(cipher.decrypt(entity.getEmailEnc()), cipher.decrypt(entity.getPasswordEnc()),
                entity.getSender(), entity.getConnectedAt());
    }

    /** Writes {@code connection} into {@code entity}, a new one or the company's existing row. */
    public void applyEskizConnection(EskizConnectionEntity entity, long companyId, EskizConnection connection) {
        entity.setCompanyId(companyId);
        entity.setEmailEnc(cipher.encrypt(connection.email()));
        entity.setPasswordEnc(cipher.encrypt(connection.password()));
        entity.setSender(connection.sender());
        entity.setConnectedAt(connection.connectedAt());
        entity.setUpdatedAt(Instant.now());
    }
}
