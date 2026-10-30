package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmConnectorPort;
import uz.murodjon.robotcallv2.crm.infrastructure.config.CrmProperties;
import uz.murodjon.robotcallv2.integration.application.port.output.CrmAccountProbePort;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnectionCheck;

import java.util.List;

/**
 * Answers the integration feature from the CRM side: the platform's Uysot address from its
 * configuration, and a connection check through whichever connector serves the provider.
 */
@Component
public class CrmAccountProbeAdapter implements CrmAccountProbePort {

    private final CrmProperties crmProperties;
    private final List<CrmConnectorPort> connectors;

    public CrmAccountProbeAdapter(CrmProperties crmProperties, List<CrmConnectorPort> connectors) {
        this.crmProperties = crmProperties;
        this.connectors = connectors;
    }

    @Override
    public String findUysotBaseUrl() {
        String baseUrl = crmProperties.baseUrl();
        return crmProperties.enabled() && baseUrl != null && !baseUrl.isBlank() ? baseUrl : null;
    }

    @Override
    public CrmConnectionCheck probe(CrmConnection connection) {
        return connectors.stream()
                .filter(connector -> connector.isSupported(connection.provider()))
                .findFirst()
                .map(connector -> connector.checkConnection(connection))
                .orElseGet(() -> CrmConnectionCheck.refused("no connector for " + connection.provider()));
    }
}
