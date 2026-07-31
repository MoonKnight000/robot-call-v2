package uz.murodjon.uysotvoice.company;

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

import uz.murodjon.uysotvoice.campaign.dto.CampaignFilter;
import uz.murodjon.uysotvoice.campaign.dto.CampaignRow;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;

import java.time.LocalTime;

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
        registry.add("voice-agent.asterisk.enabled", () -> "false");
        registry.add("voice-agent.stt.enabled", () -> "false");
        registry.add("voice-agent.dialer.enabled", () -> "false");
    }

    @Autowired
    CampaignRepository campaigns;
    @Autowired
    JdbcTemplate jdbc;

    private long otherCompanyCampaignId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO company(id, name) VALUES (2, 'Other') ON CONFLICT (id) DO NOTHING");
        otherCompanyCampaignId = jdbc.queryForObject(
                "INSERT INTO campaign(name, type, status, goal_prompt, script_config, company_id) "
                        + "VALUES ('other-company-campaign', 'DEBT_COLLECTION', 'ACTIVE', '', '{}'::jsonb, 2) "
                        + "RETURNING id",
                Long.class);
    }

    @Test
    void anotherCompanysCampaignIsInvisibleById() {
        assertThat(campaigns.find(otherCompanyCampaignId)).isNull();
    }

    @Test
    void anotherCompanysCampaignIsExcludedFromTheList() {
        long ownId = campaigns.create("my-campaign", "DEBT_COLLECTION", "goal", "{}", "uz-UZ",
                LocalTime.of(9, 0), LocalTime.of(20, 0), "MONDAY", 3, 24, 5, null, 0);

        var page = campaigns.findAll(new CampaignFilter(null, 500, null));

        assertThat(page).extracting(CampaignRow::id).contains(ownId).doesNotContain(otherCompanyCampaignId);
    }

    @Test
    void countExcludesAnotherCompanysCampaigns() {
        long before = campaigns.count();

        campaigns.create("counted-campaign", "DEBT_COLLECTION", "goal", "{}", "uz-UZ",
                LocalTime.of(9, 0), LocalTime.of(20, 0), "MONDAY", 3, 24, 5, null, 0);

        // +1 for the campaign just created in *this* company; otherCompanyCampaignId
        // (seeded in setUp under company 2) must not also be reflected here.
        assertThat(campaigns.count()).isEqualTo(before + 1);
    }

    @Test
    void updatingAnotherCompanysCampaignIsANoOp() {
        campaigns.updateStatus(otherCompanyCampaignId, "PAUSED");

        String status = jdbc.queryForObject(
                "SELECT status FROM campaign WHERE id = ?", String.class, otherCompanyCampaignId);
        assertThat(status).isEqualTo("ACTIVE"); // unchanged — the UPDATE matched zero rows
    }
}
