package uz.murodjon.robotcallv2.integration.application.port.output;

import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;

import java.time.Instant;
import java.util.Optional;

public interface CrmIntegrationRepository {

    Optional<CrmIntegration> find(long companyId);

    CrmIntegration saveAppInfo(long companyId, String appName, String grantsJson);

    CrmIntegration applyTokenResponse(long companyId, String accessTokenEnc, String refreshTokenEnc,
                                      Instant tokenExpiresAt);

    void markError(long companyId);

    void disconnect(long companyId);
}
