package uz.murodjon.robotcallv2.campaign.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the dialer's claim query against a real PostgreSQL, because everything
 * that matters here is database behaviour: {@code FOR UPDATE SKIP LOCKED}, the
 * {@code RETURNING} clause and the opt-out join do not exist in a mock.
 *
 * <p>The property under test is that a target is handed to exactly one claimer. Two
 * dialer instances calling the old select-then-update pair could both pick the same
 * row and call the same person twice.
 */
@SpringBootTest
@Testcontainers
class CampaignTargetRepositoryTest {

    /** The tenant R__seed_data creates; every repository call names it explicitly. */
    private static final long COMPANY_ID = 1L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void isolateFromExternalServices(DynamicPropertyRegistry registry) {
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("management.health.rabbit.enabled", () -> "false");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("voice-agent.asterisk.enabled", () -> "false");
        registry.add("voice-agent.stt.enabled", () -> "false");
        // The scheduled dialer would race this test for the very rows it claims.
        registry.add("voice-agent.dialer.enabled", () -> "false");
        registry.add("spring.ai.google.genai.api-key", () -> "test-key");
    }

    @Autowired
    CampaignTargetRepository targets;
    @Autowired
    CampaignRepository campaigns;
    @Autowired
    DoNotCallRepository doNotCall;
    @Autowired
    JdbcTemplate jdbc;

    private long campaignId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM do_not_call_list");
        // A campaign speaks as an agent (ai_agent_id NOT NULL) and nothing seeds one, so the
        // test brings its own; every other column of the row has a default.
        long agentId = jdbc.queryForObject(
                "INSERT INTO ai_agent(company_id, name) VALUES (?, 'claim-test-agent') RETURNING id",
                Long.class, COMPANY_ID);
        campaignId = campaigns.create(COMPANY_ID, new Campaign(0, "claim-test", CampaignType.DEBT_COLLECTION,
                CampaignStatus.DRAFT, LocalTime.of(9, 0), LocalTime.of(20, 0),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                3, 24, 5, 0, agentId, COMPANY_ID, null, RecurrenceType.ONCE, null, null, false, null));
    }

    private long addTarget(String phone) {
        return targets.add(COMPANY_ID, CampaignTarget.queued(campaignId, 1L, phone, "uz-UZ", "{}"));
    }

    @Test
    void claimMarksInProgressAndCountsTheAttempt() {
        addTarget("998900000001");

        List<CampaignTarget> claimed = targets.claimDue(campaignId, 10);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).status()).isEqualTo(TargetStatus.IN_PROGRESS);
        assertThat(claimed.get(0).attempts()).isEqualTo(1);
    }

    @Test
    void aTargetIsNeverHandedOutTwice() {
        addTarget("998900000001");
        addTarget("998900000002");
        addTarget("998900000003");

        List<CampaignTarget> first = targets.claimDue(campaignId, 2);
        List<CampaignTarget> second = targets.claimDue(campaignId, 2);

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(1); // only one PENDING row was left
        assertThat(first.stream().map(CampaignTarget::id))
                .doesNotContainAnyElementsOf(second.stream().map(CampaignTarget::id).toList());
    }

    @Test
    void respectsTheRequestedLimit() {
        addTarget("998900000001");
        addTarget("998900000002");
        addTarget("998900000003");

        assertThat(targets.claimDue(campaignId, 2)).hasSize(2);
    }

    @Test
    void skipsNumbersOnThePhoneLevelOptOutList() {
        // §11.4: the opt-out has to hold for campaigns the client was never part of,
        // so it is matched on the phone number, not on the target row.
        addTarget("998900000001");
        long allowed = addTarget("998900000002");
        doNotCall.add(COMPANY_ID, "998900000001", "asked not to be called", DoNotCallSource.CALL);

        List<CampaignTarget> claimed = targets.claimDue(campaignId, 10);

        assertThat(claimed).extracting(CampaignTarget::id).containsExactly(allowed);
    }

    @Test
    void skipsTargetsFlaggedOnTheRow() {
        long flagged = addTarget("998900000001");
        long allowed = addTarget("998900000002");
        targets.setDoNotCall(COMPANY_ID, flagged);

        List<CampaignTarget> claimed = targets.claimDue(campaignId, 10);

        assertThat(claimed).extracting(CampaignTarget::id).containsExactly(allowed);
    }

    /**
     * A target the contact policy holds back was claimed but never dialled: it goes back
     * to waiting, not due before the policy allows, and the attempt the claim counted is
     * given back — a number that was not called must not use up its attempts.
     */
    @Test
    void releasingAClaimGivesTheAttemptBackAndHoldsTheTarget() {
        long id = addTarget("998900000001");
        targets.claimDue(campaignId, 10);

        targets.releaseClaim(id, Instant.now().plus(2, ChronoUnit.DAYS));

        CampaignTarget released = targets.find(COMPANY_ID, id);
        assertThat(released.status()).isEqualTo(TargetStatus.PENDING);
        assertThat(released.attempts()).isZero();
        assertThat(targets.claimDue(campaignId, 10)).isEmpty(); // not due yet
    }

    @Test
    void aggregatesTargetStatsCorrectly() {
        long t1 = addTarget("998900000001");
        addTarget("998900000002");
        addTarget("998900000003");

        // t1 is called & completed
        targets.updateStatus(t1, TargetStatus.DONE, null);
        // t2 is claimed (in progress)
        targets.claimDue(campaignId, 1);
        // t3 stays pending

        CampaignTargetStats stats = targets.statsByCampaignId(COMPANY_ID, campaignId);
        assertThat(stats.totalTargets()).isEqualTo(3L);
        assertThat(stats.calledTargets()).isEqualTo(2L);
        assertThat(stats.pendingTargets()).isEqualTo(1L);
        assertThat(stats.completedTargets()).isEqualTo(1L);

        Map<Long, CampaignTargetStats> map = targets.statsByCampaignIds(COMPANY_ID, List.of(campaignId));
        assertThat(map).containsKey(campaignId);
        assertThat(map.get(campaignId).totalTargets()).isEqualTo(3L);
        assertThat(map.get(campaignId).calledTargets()).isEqualTo(2L);
        assertThat(map.get(campaignId).pendingTargets()).isEqualTo(1L);
        assertThat(map.get(campaignId).completedTargets()).isEqualTo(1L);
    }
}
