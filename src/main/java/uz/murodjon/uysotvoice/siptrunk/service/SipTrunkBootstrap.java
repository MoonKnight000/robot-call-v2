package uz.murodjon.uysotvoice.siptrunk.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.ari.AsteriskProperties;
import uz.murodjon.uysotvoice.siptrunk.repository.SipTrunkRepository;

/**
 * One-time migration path for the pre-B.3 world, where the trunk was a single
 * {@code voice-agent.asterisk.trunk-endpoint} config value instead of a {@code
 * sip_trunk} row: on first startup after this feature ships, if the default company
 * has no default trunk yet, seed one from that already-configured value — so an
 * existing deployment keeps dialling through the exact same PJSIP endpoint it always
 * has, with no manual re-entry through the new API.
 *
 * <p>Runs every startup but is a no-op once the row exists ({@link
 * SipTrunkRepository#hasDefault}).
 */
@Component
public class SipTrunkBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SipTrunkBootstrap.class);

    private final SipTrunkRepository trunks;
    private final AsteriskProperties asterisk;

    public SipTrunkBootstrap(SipTrunkRepository trunks, AsteriskProperties asterisk) {
        this.trunks = trunks;
        this.asterisk = asterisk;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultTrunkFromConfig() {
        if (trunks.hasDefault()) {
            return;
        }
        if (asterisk.trunkEndpoint() == null || asterisk.trunkEndpoint().isBlank()) {
            log.warn("No sip_trunk default and voice-agent.asterisk.trunk-endpoint is blank — "
                    + "outbound calls will fail until a default trunk is created via POST /api/sip-trunks");
            return;
        }
        String callerId = asterisk.callerId() == null || asterisk.callerId().isBlank() ? null : asterisk.callerId();
        long id = trunks.create("Default", asterisk.trunkEndpoint(), callerId, true);
        log.info("Seeded default SIP trunk {} (endpoint '{}') from voice-agent.asterisk.trunk-endpoint",
                id, asterisk.trunkEndpoint());
    }
}
