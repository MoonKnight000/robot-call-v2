package uz.murodjon.robotcallv2.agent.ami;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.dialer.application.port.output.TrunkRegistrationStatusPort;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches Asterisk's outbound SIP registrations and says so the moment one drops.
 *
 * <p>A trunk that loses its registration keeps accepting originates and fails every one
 * of them — on this deployment that looked like a campaign whose calls all ended in a few
 * seconds with cause 18, which reads as a network fault on the callee's side. The trunk
 * CRUD knows which endpoint a call uses, never whether it is alive; this is the part that
 * knows. Exported as {@code voice.trunk.registered{registration}} for dashboards, and a
 * transition away from {@code Registered} is logged as an alert and raised as a
 * notification.
 *
 * <p>Read through AMI ({@code pjsip show registrations}) because ARI has no view of
 * registrations. Does nothing while AMI is switched off.
 */
@Component
public class TrunkRegistrationMonitor implements TrunkRegistrationStatusPort {

    private static final Logger log = LoggerFactory.getLogger(TrunkRegistrationMonitor.class);

    private static final String REGISTERED = "Registered";

    /**
     * One registration row: {@code <name>/<server-uri>  <auth>  <status>  (exp. Ns)}. The
     * header row starts with {@code <Registration/} and never carries a status word.
     */
    private static final Pattern ROW = Pattern.compile(
            "^\\s*([^\\s/<]+)/\\S+.*?\\b(Registered|Unregistered|Rejected|Stopping|Stopped)\\b");

    private final AmiClient amiClient;
    private final AmiProperties amiProperties;
    private final CallMetrics metrics;
    private final NotificationUseCase notificationService;
    private final CompanyProperties companyProperties;

    /** Last status seen per registration, so only a change is alerted on. */
    private final Map<String, String> lastStatus = new ConcurrentHashMap<>();

    /**
     * Whether Asterisk reported this registration as anything but Registered. Unknown —
     * AMI off, or a registration by another name — is not "down": the dialer must not stop
     * dialling because it cannot see.
     */
    @Override
    public boolean isKnownDown(String registration) {
        if (registration == null) {
            return false;
        }
        String status = lastStatus.get(registration);
        return status != null && !REGISTERED.equals(status);
    }

    public TrunkRegistrationMonitor(AmiClient amiClient, AmiProperties amiProperties, CallMetrics metrics,
                                    NotificationUseCase notificationService, CompanyProperties companyProperties) {
        this.amiClient = amiClient;
        this.amiProperties = amiProperties;
        this.metrics = metrics;
        this.notificationService = notificationService;
        this.companyProperties = companyProperties;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.asterisk.ami.registration-check-seconds:60} * 1000}",
            initialDelayString = "#{${voice-agent.asterisk.ami.registration-check-seconds:60} * 1000}")
    public void check() {
        if (!amiProperties.enabled()) {
            return;
        }
        try {
            String output = amiClient.executeCommand("pjsip show registrations");
            if (output == null) {
                return;
            }
            for (String line : output.split("\n")) {
                Matcher row = ROW.matcher(line);
                if (row.find()) {
                    record(row.group(1), row.group(2));
                }
            }
        } catch (Exception e) {
            log.warn("SIP registration check failed: {}", e.getMessage());
        }
    }

    private void record(String registration, String status) {
        boolean registered = REGISTERED.equals(status);
        metrics.trunkRegistration(registration, registered);
        String previous = lastStatus.put(registration, status);
        if (status.equals(previous)) {
            return;
        }
        if (!registered) {
            log.error("ALERT: SIP registration {} is {} — outbound calls through it will fail", registration, status);
            // Platform-wide, as the rest of AlertingService's checks: the trunk belongs to
            // the deployment's Asterisk, not to one tenant.
            notificationService.notify(companyProperties.defaultId(), NotificationType.ERROR_OCCURRED,
                    "SIP trunk uzildi", "SIP ro'yxatdan o'tish " + registration + " holati: " + status
                            + ". Shu trunk orqali qo'ng'iroqlar ketmaydi.", null);
        } else if (previous != null) {
            log.info("SIP registration {} recovered ({} -> {})", registration, previous, status);
        }
    }
}
