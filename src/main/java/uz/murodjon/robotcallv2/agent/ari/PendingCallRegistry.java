package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manual, test and browser calls between the moment they are placed and the StasisStart
 * that follows — what the StasisStart thread, which has no request behind it, needs to know
 * about them — and the campaign profile of a browser test while it runs.
 */
@Component
class PendingCallRegistry {

    private static final Logger log = LoggerFactory.getLogger(PendingCallRegistry.class);

    /** {@link #pendingManualScenarios} sentinel: a manual call with no explicit {@code scenarioId}. */
    static final long NO_EXPLICIT_SCENARIO = 0L;
    /** How long a browser has to dial in after {@link WebTestCallService#prepareWebTest} before its session is forgotten. */
    private static final Duration WEB_TEST_SESSION_TTL = Duration.ofMinutes(5);

    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;

    /**
     * A manual test call's chosen scenario (ROADMAP A.3, {@code POST /api/calls?
     * scenarioId=}), correlated by channel id between {@link OutboundDialService#originateManualCall}
     * and the {@code StasisStart} that follows it — mirrors how {@link OutboundCallTrackingUseCase}
     * correlates an outbound call to its context.
     */
    private final Map<String, Long> pendingManualScenarios = new ConcurrentHashMap<>();

    /**
     * An ad-hoc (unsaved) scenario draft to test-drive (backend-uchun-talablar.md §4,
     * {@code POST /api/calls/test}), correlated by channel id the same way {@link
     * #pendingManualScenarios} is. Checked first in {@link WebTestCallService#resolveManualScenario}
     * — a channel here never also has a real {@code pendingManualScenarios} entry.
     */
    private final Map<String, ScenarioDefinition> pendingManualDefinitions = new ConcurrentHashMap<>();

    /**
     * The published agent a manual call runs ({@code POST /api/calls?agentId=}) — its own
     * scenario, voice and prompt — correlated by channel id the same way. A channel here has
     * no {@link #pendingManualScenarios} entry: the agent brings its scenario.
     */
    private final Map<String, AiAgent> pendingManualAgents = new ConcurrentHashMap<>();

    /**
     * The company a manual/test call was placed for, kept until its {@code StasisStart}
     * arrives — that thread has no request behind it, so the tenant cannot be re-derived.
     */
    private final Map<String, Long> pendingManualCompanies = new ConcurrentHashMap<>();

    /**
     * Browser test calls handed out by {@link WebTestCallService#prepareWebTest}, keyed by
     * session id until the browser dials in with it (extensions.conf passes it as the second
     * Stasis argument after {@code web-test}); {@link #claimWebTest} then moves the call
     * onto its channel.
     */
    private final Map<String, WebTestCall> pendingWebTests = new ConcurrentHashMap<>();

    /**
     * The campaign profile of a browser test call, keyed by channel. Deliberately kept out
     * of {@link OutboundCallTrackingUseCase}: a test holds no dialer slot and has no campaign
     * target to settle, so teardown must find nothing there.
     */
    private final Map<String, OutboundCall> webTestCalls = new ConcurrentHashMap<>();

    PendingCallRegistry(OutboundCallTrackingUseCase outboundCallTrackingUseCase) {
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
    }

    void registerManualScenario(String channelId, Long scenarioId, long companyId) {
        pendingManualScenarios.put(channelId, scenarioId != null ? scenarioId : NO_EXPLICIT_SCENARIO);
        pendingManualCompanies.put(channelId, companyId);
    }

    void registerManualDefinition(String channelId, ScenarioDefinition definition, long companyId) {
        pendingManualDefinitions.put(channelId, definition);
        pendingManualCompanies.put(channelId, companyId);
    }

    void registerManualAgent(String channelId, AiAgent agent, long companyId) {
        pendingManualAgents.put(channelId, agent);
        pendingManualCompanies.put(channelId, companyId);
    }

    void purgeExpiredWebTests() {
        Instant cutoff = Instant.now().minus(WEB_TEST_SESSION_TTL);
        pendingWebTests.values().removeIf(test -> test.createdAt().isBefore(cutoff));
    }

    void registerWebTest(String sessionId, WebTestCall test) {
        pendingWebTests.put(sessionId, test);
    }

    /**
     * Moves the web test session the browser dialled in with onto its channel, so
     * {@link CallSetupService} finds it the way it finds any other test call. False when the
     * session is unknown or already expired — the channel is then hung up.
     */
    boolean claimWebTest(String channelId, String sessionId) {
        WebTestCall test = sessionId != null ? pendingWebTests.remove(sessionId) : null;
        if (test == null) {
            log.warn("Web test call {} carries unknown or expired session {} — hanging up", channelId, sessionId);
            return false;
        }
        if (test.outbound() != null) {
            webTestCalls.put(channelId, test.outbound());
        } else if (test.definition() != null) {
            pendingManualDefinitions.put(channelId, test.definition());
            pendingManualCompanies.put(channelId, test.companyId());
        } else {
            pendingManualScenarios.put(channelId, test.scenarioId() != null ? test.scenarioId() : NO_EXPLICIT_SCENARIO);
            pendingManualCompanies.put(channelId, test.companyId());
        }
        log.info("Web test session {} claimed by channel {}", sessionId, channelId);
        return true;
    }

    /** Whether {@code channelId} is a manual or test call still waiting for its setup. */
    boolean isManual(String channelId) {
        return pendingManualScenarios.containsKey(channelId) || pendingManualDefinitions.containsKey(channelId)
                || pendingManualAgents.containsKey(channelId);
    }

    /** The agent a manual call was placed with, or null when it runs a bare scenario. */
    AiAgent takeManualAgent(String channelId) {
        return pendingManualAgents.remove(channelId);
    }

    Long takeManualCompany(String channelId) {
        return pendingManualCompanies.remove(channelId);
    }

    ScenarioDefinition takeManualDefinition(String channelId) {
        return pendingManualDefinitions.remove(channelId);
    }

    Long takeManualScenarioId(String channelId) {
        return pendingManualScenarios.remove(channelId);
    }

    /** The campaign profile behind a channel: a dialer call, or a browser test of a campaign. */
    OutboundCall findOutboundProfile(String channelId) {
        OutboundCall outbound = outboundCallTrackingUseCase.peek(channelId);
        return outbound != null ? outbound : webTestCalls.get(channelId);
    }

    /**
     * A channel that is gone leaves nothing waiting for it — the company of a manual or test
     * call that was never answered included, which used to stay here for good.
     */
    void forgetUnanswered(String channelId) {
        pendingManualScenarios.remove(channelId);
        pendingManualDefinitions.remove(channelId);
        pendingManualAgents.remove(channelId);
        pendingManualCompanies.remove(channelId);
        webTestCalls.remove(channelId);
    }

    void forgetWebTestCall(String channelId) {
        webTestCalls.remove(channelId);
    }
}
