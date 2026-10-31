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
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code company_id} filtering across the repository layer (ROADMAP B.2)
 * actually excludes another company's rows, rather than only compiling.
 *
 * <p>The company is an explicit argument of every repository call — it comes from the
 * request's token, never from a thread-local — so "this instance's" tenant is simply
 * the one passed in: {@value #OWN_COMPANY_ID}, the default R__seed_data creates. A second
 * company's data is seeded with raw JDBC, standing in for "some other tenant's row that
 * must never surface through a call made for company 1."
 */
@SpringBootTest
@Testcontainers
class CompanyIsolationTest {

    private static final long OWN_COMPANY_ID = 1L;
    private static final long OTHER_COMPANY_ID = 2L;

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
    private long ownAgentId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO company(id, name) VALUES (?, 'Other') ON CONFLICT (id) DO NOTHING", OTHER_COMPANY_ID);
        // A campaign speaks as an agent (ai_agent_id NOT NULL); nothing seeds one.
        ownAgentId = agentOf(OWN_COMPANY_ID);
        long otherAgentId = agentOf(OTHER_COMPANY_ID);
        otherCompanyCampaignId = jdbc.queryForObject(
                "INSERT INTO campaign(company_id, ai_agent_id, name, type, status, script_config) "
                        + "VALUES (?, ?, 'other-company-campaign', 'DEBT_COLLECTION', 'ACTIVE', '{}'::jsonb) "
                        + "RETURNING id",
                Long.class, OTHER_COMPANY_ID, otherAgentId);
    }

    private long agentOf(long companyId) {
        return jdbc.queryForObject(
                "INSERT INTO ai_agent(company_id, name) VALUES (?, 'isolation-agent') RETURNING id",
                Long.class, companyId);
    }

    private Campaign ownCampaign(String name) {
        return new Campaign(0, name, CampaignStatus.DRAFT,
                LocalTime.of(9, 0), LocalTime.of(20, 0), EnumSet.of(DayOfWeek.MONDAY),
                3, 24, 5, 0, ownAgentId, OWN_COMPANY_ID, null, RecurrenceType.ONCE, null, null, false, null);
    }

    @Test
    void anotherCompanysCampaignIsInvisibleById() {
        assertThat(campaigns.find(OWN_COMPANY_ID, otherCompanyCampaignId)).isNull();
    }

    @Test
    void anotherCompanysCampaignIsExcludedFromTheList() {
        long ownId = campaigns.create(OWN_COMPANY_ID, ownCampaign("my-campaign"));

        var page = campaigns.findAll(OWN_COMPANY_ID, new CampaignFilter(null, 500, null, null));

        assertThat(page).extracting(Campaign::id).contains(ownId).doesNotContain(otherCompanyCampaignId);
    }

    @Test
    void countExcludesAnotherCompanysCampaigns() {
        long before = campaigns.countByCompanyId(OWN_COMPANY_ID);

        campaigns.create(OWN_COMPANY_ID, ownCampaign("counted-campaign"));

        // +1 for the campaign just created in *this* company; otherCompanyCampaignId
        // (seeded in setUp under company 2) must not also be reflected here.
        assertThat(campaigns.countByCompanyId(OWN_COMPANY_ID)).isEqualTo(before + 1);
    }

    @Test
    void updatingAnotherCompanysCampaignIsANoOp() {
        campaigns.updateStatus(OWN_COMPANY_ID, otherCompanyCampaignId, CampaignStatus.PAUSED);

        String status = jdbc.queryForObject(
                "SELECT status FROM campaign WHERE id = ?", String.class, otherCompanyCampaignId);
        assertThat(status).isEqualTo("ACTIVE"); // unchanged — the UPDATE matched zero rows
    }
}
