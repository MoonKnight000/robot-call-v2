package uz.murodjon.robotcallv2.company;

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

import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code company_id} filtering added across the repository layer
 * (ROADMAP B.2) actually excludes another company's rows, rather than only compiling.
 *
 * <p>{@link CurrentCompany} in this process always resolves to the single configured
 * default (id 1 — see {@code voice-agent.company.default-id}), since there is no
 * per-request resolution yet. A second company's data is therefore seeded directly
 * with raw JDBC here, standing in for "some other tenant's row that must never surface
 * through this instance's repositories."
 */
@SpringBootTest
@Testcontainers
class CompanyIsolationTest {

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
        registry.add("voice-agent.dialer.enabled", () -> "false");
        registry.add("spring.ai.google.genai.api-key", () -> "test-key");
    }

    @Autowired
    CampaignRepository campaigns;
    @Autowired
    JdbcTemplate jdbc;

    private long otherCompanyCampaignId;
    private long scenarioId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO company(id, name) VALUES (2, 'Other') ON CONFLICT (id) DO NOTHING");
        scenarioId = jdbc.queryForObject(
                "SELECT id FROM scenario WHERE scenario_key = 'debt-collection' AND is_active", Long.class);
        otherCompanyCampaignId = jdbc.queryForObject(
                "INSERT INTO campaign(name, type, status, goal_prompt, script_config, company_id, scenario_id) "
                        + "VALUES ('other-company-campaign', 'DEBT_COLLECTION', 'ACTIVE', '', '{}'::jsonb, 2, ?) "
                        + "RETURNING id",
                Long.class, scenarioId);
    }

    @Test
    void anotherCompanysCampaignIsInvisibleById() {
        assertThat(campaigns.find(otherCompanyCampaignId)).isNull();
    }

    @Test
    void anotherCompanysCampaignIsExcludedFromTheList() {
        long ownId = campaigns.create(new Campaign(0, "my-campaign", CampaignType.DEBT_COLLECTION, CampaignStatus.DRAFT,
                "goal", "uz-UZ", LocalTime.of(9, 0), LocalTime.of(20, 0), EnumSet.of(DayOfWeek.MONDAY),
                3, 24, 5, null, 0, scenarioId, 0, true, null));

        var page = campaigns.findAll(new CampaignFilter(null, 500, null, null));

        assertThat(page).extracting(Campaign::id).contains(ownId).doesNotContain(otherCompanyCampaignId);
    }

    @Test
    void countExcludesAnotherCompanysCampaigns() {
        long before = campaigns.count();

        campaigns.create(new Campaign(0, "counted-campaign", CampaignType.DEBT_COLLECTION, CampaignStatus.DRAFT,
                "goal", "uz-UZ", LocalTime.of(9, 0), LocalTime.of(20, 0), EnumSet.of(DayOfWeek.MONDAY),
                3, 24, 5, null, 0, scenarioId, 0, true, null));

        // +1 for the campaign just created in *this* company; otherCompanyCampaignId
        // (seeded in setUp under company 2) must not also be reflected here.
        assertThat(campaigns.count()).isEqualTo(before + 1);
    }

    @Test
    void updatingAnotherCompanysCampaignIsANoOp() {
        campaigns.updateStatus(1L, otherCompanyCampaignId, CampaignStatus.PAUSED);

        String status = jdbc.queryForObject(
                "SELECT status FROM campaign WHERE id = ?", String.class, otherCompanyCampaignId);
        assertThat(status).isEqualTo("ACTIVE"); // unchanged — the UPDATE matched zero rows
    }
}
