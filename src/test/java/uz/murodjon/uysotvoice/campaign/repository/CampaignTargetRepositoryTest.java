package uz.murodjon.uysotvoice.campaign.repository;

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

import uz.murodjon.uysotvoice.campaign.dto.TargetRow;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;

import java.time.LocalTime;
import java.util.List;

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

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void isolateFromExternalServices(DynamicPropertyRegistry registry) {
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("management.health.rabbit.enabled", () -> "false");
        registry.add("voice-agent.asterisk.enabled", () -> "false");
        registry.add("voice-agent.stt.enabled", () -> "false");
        // The scheduled dialer would race this test for the very rows it claims.
        registry.add("voice-agent.dialer.enabled", () -> "false");
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
        campaignId = campaigns.create("claim-test", "DEBT_COLLECTION", "goal", "{}", "uz-UZ",
                LocalTime.of(9, 0), LocalTime.of(20, 0), "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
                3, 24, 5, null, 0);
    }

    private long addTarget(String phone) {
        return targets.add(campaignId, 1L, phone, "uz-UZ", "{}");
    }

    @Test
    void claimMarksInProgressAndCountsTheAttempt() {
        addTarget("998900000001");

        List<TargetRow> claimed = targets.claimDue(campaignId, 10);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).status()).isEqualTo("IN_PROGRESS");
        assertThat(claimed.get(0).attempts()).isEqualTo(1);
    }

    @Test
    void aTargetIsNeverHandedOutTwice() {
        addTarget("998900000001");
        addTarget("998900000002");
        addTarget("998900000003");

        List<TargetRow> first = targets.claimDue(campaignId, 2);
        List<TargetRow> second = targets.claimDue(campaignId, 2);

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(1); // only one PENDING row was left
        assertThat(first.stream().map(TargetRow::id))
                .doesNotContainAnyElementsOf(second.stream().map(TargetRow::id).toList());
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
        doNotCall.add("998900000001", "asked not to be called", "CALL");

        List<TargetRow> claimed = targets.claimDue(campaignId, 10);

        assertThat(claimed).extracting(TargetRow::id).containsExactly(allowed);
    }

    @Test
    void skipsTargetsFlaggedOnTheRow() {
        long flagged = addTarget("998900000001");
        long allowed = addTarget("998900000002");
        targets.setDoNotCall(flagged);

        List<TargetRow> claimed = targets.claimDue(campaignId, 10);

        assertThat(claimed).extracting(TargetRow::id).containsExactly(allowed);
    }

    @Test
    void skipsTargetsWhoseRetryTimeHasNotArrived() {
        long later = addTarget("998900000001");
        long now = addTarget("998900000002");
        jdbc.update("UPDATE campaign_target SET status = 'PENDING', next_attempt_at = now() + interval '1 hour' "
                + "WHERE id = ?", later);

        List<TargetRow> claimed = targets.claimDue(campaignId, 10);

        assertThat(claimed).extracting(TargetRow::id).containsExactly(now);
    }

    @Test
    void claimsNothingWhenEveryTargetIsDone() {
        long id = addTarget("998900000001");
        targets.updateStatus(id, "DONE", null);

        assertThat(targets.claimDue(campaignId, 10)).isEmpty();
    }

    @Test
    void optOutListIgnoresDuplicates() {
        doNotCall.add("998900000001", "first", "CALL");
        doNotCall.add("998900000001", "again", "CALL");

        assertThat(doNotCall.contains("998900000001")).isTrue();
        Long rows = jdbc.queryForObject(
                "SELECT count(*) FROM do_not_call_list WHERE phone = ?", Long.class, "998900000001");
        assertThat(rows).isEqualTo(1L);
    }
}
