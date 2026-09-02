package uz.murodjon.robotcallv2.campaign.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignTableField;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;

import java.time.Instant;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignFilterTest {

    @Test
    void defaultSortIsDeterministicCreatedAtDescAndIdDesc() {
        CampaignFilter filter = new CampaignFilter(null, null, null, null);

        assertThat(filter.orders()).isNotNull();
        assertThat(filter.orders()).containsExactly(
                java.util.Map.entry(CampaignTableField.CREATED_AT, Sort.Direction.DESC),
                java.util.Map.entry(CampaignTableField.ID, Sort.Direction.DESC)
        );
    }

    @Test
    void customSortAppendsIdDescTieBreakerIfNotPresent() {
        LinkedHashMap<CampaignTableField, Sort.Direction> customOrders = new LinkedHashMap<>();
        customOrders.put(CampaignTableField.NAME, Sort.Direction.ASC);

        CampaignFilter filter = new CampaignFilter(0, 20, customOrders, "test", CampaignStatus.ACTIVE,
                CampaignType.DEBT_COLLECTION, 1L, 2L, RecurrenceType.ONCE, Instant.now(), Instant.now());

        assertThat(filter.orders()).containsExactly(
                java.util.Map.entry(CampaignTableField.NAME, Sort.Direction.ASC),
                java.util.Map.entry(CampaignTableField.ID, Sort.Direction.DESC)
        );
    }

    @Test
    void customSortPreservesExplicitIdOrdering() {
        LinkedHashMap<CampaignTableField, Sort.Direction> customOrders = new LinkedHashMap<>();
        customOrders.put(CampaignTableField.ID, Sort.Direction.ASC);

        CampaignFilter filter = new CampaignFilter(0, 20, customOrders, null, null, null, null, null, null, null, null);

        assertThat(filter.orders()).containsExactly(
                java.util.Map.entry(CampaignTableField.ID, Sort.Direction.ASC)
        );
    }
}
