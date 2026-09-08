package uz.murodjon.robotcallv2.secret.application.port.input;

import uz.murodjon.robotcallv2.secret.application.dto.CreateSecretRequest;
import uz.murodjon.robotcallv2.secret.application.dto.SecretRow;
import uz.murodjon.robotcallv2.secret.application.dto.UpdateSecretRequest;

import java.util.List;
import java.util.Map;

public interface SecretUseCase {

    SecretRow createSecret(long companyId, CreateSecretRequest request);

    SecretRow updateSecret(long companyId, long id, UpdateSecretRequest request);

    SecretRow findSecret(long companyId, long id);

    List<SecretRow> findSecrets(long companyId);

    void deleteSecret(long companyId, long id);

    /** Replaces every {{secrets.KEY}} placeholder in the text with the company's value. */
    String resolveSecrets(long companyId, String text);

    /**
     * The company's secrets in the clear, for building an outgoing tool or webhook call.
     * A secret that cannot be decrypted is absent rather than present and wrong.
     */
    Map<String, String> findDecryptedSecrets(long companyId);
}
