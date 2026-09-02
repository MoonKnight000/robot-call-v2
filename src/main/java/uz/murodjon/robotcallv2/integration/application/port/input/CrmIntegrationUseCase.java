package uz.murodjon.robotcallv2.integration.application.port.input;

import uz.murodjon.robotcallv2.integration.application.dto.AuthorizeUrlResponse;
import uz.murodjon.robotcallv2.integration.application.dto.ConnectIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmCatalogEntry;
import uz.murodjon.robotcallv2.integration.application.dto.CrmIntegrationRow;

import java.util.List;
import java.util.Optional;

public interface CrmIntegrationUseCase {

    List<CrmCatalogEntry> catalog();

    CrmIntegrationRow find();

    CrmIntegrationRow connect(ConnectIntegrationRequest r);

    AuthorizeUrlResponse buildAuthorizeUrl();

    void handleCallback(String code, String state);

    Optional<String> currentAccessToken(long companyId);

    void disconnect();
}
