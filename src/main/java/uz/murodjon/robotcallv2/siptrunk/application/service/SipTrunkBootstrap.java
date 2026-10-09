package uz.murodjon.robotcallv2.siptrunk.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.ari.AsteriskProperties;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.SipTrunkRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.util.List;

@Component
public class SipTrunkBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SipTrunkBootstrap.class);

    private final SipTrunkRepository sipTrunkRepository;
    private final AsteriskProperties asterisk;
    private final CompanyProperties companyProperties;

    public SipTrunkBootstrap(SipTrunkRepository sipTrunkRepository, AsteriskProperties asterisk,
                             CompanyProperties companyProperties) {
        this.sipTrunkRepository = sipTrunkRepository;
        this.asterisk = asterisk;
        this.companyProperties = companyProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultTrunkFromConfig() {
        long companyId = companyProperties.defaultId();
        if (sipTrunkRepository.hasDefault(companyId)) {
            return;
        }
        if (asterisk.trunkEndpoint() == null || asterisk.trunkEndpoint().isBlank()) {
            log.warn("No sip_trunk default and voice-agent.asterisk.trunk-endpoint is blank — "
                    + "outbound calls will fail until a default trunk is created via POST /api/sip-trunks");
            return;
        }
        String callerId = asterisk.callerId() == null || asterisk.callerId().isBlank() ? null : asterisk.callerId();
        long id = sipTrunkRepository.create(companyId, "Default", asterisk.trunkEndpoint(), callerId, true,
                null, 5060, null, null, SipTrunkTransport.UDP, List.of("alaw", "ulaw"), null);
        log.info("Seeded default SIP trunk {} (endpoint '{}') from voice-agent.asterisk.trunk-endpoint",
                id, asterisk.trunkEndpoint());
    }
}
