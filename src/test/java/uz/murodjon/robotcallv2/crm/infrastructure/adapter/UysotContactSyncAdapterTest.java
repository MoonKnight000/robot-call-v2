package uz.murodjon.robotcallv2.crm.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactRecord;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UysotContactSyncAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void leadBecomesOneRecordWithEveryContactPhoneAndMappedFields() throws Exception {
        JsonNode lead = objectMapper.readTree("""
                {
                  "id": 77, "name": "Lead 77", "updatedTimestamp": 1762416000,
                  "fields": [
                    { "id": 10, "value": "Chilonzor" },
                    { "id": 11, "valueList": ["2 xona", "3 xona"] },
                    { "id": 12, "number": 85000 },
                    { "id": 13, "value": "not mapped" }
                  ],
                  "contacts": [
                    { "id": 1, "name": "Aziz Karimov", "phones": ["+998901234567", "+998911112233"],
                      "fields": [ { "id": 20, "value": "aziz@mail.uz" }, { "id": 10, "value": "contact wins not" } ] },
                    { "id": 2, "name": "Malika", "phones": ["+998935556677"] }
                  ]
                }
                """);

        CrmContactRecord record = UysotContactSyncAdapter.toRecord(77, lead, lead, Set.of("10", "11", "12", "20"));

        assertThat(record.externalId()).isEqualTo("77");
        assertThat(record.name()).isEqualTo("Aziz Karimov");
        assertThat(record.phones()).containsExactly("+998901234567", "+998911112233", "+998935556677");
        assertThat(record.fields()).containsEntry("10", "Chilonzor").containsEntry("11", "2 xona, 3 xona")
                .containsEntry("12", "85000").containsEntry("20", "aziz@mail.uz").doesNotContainKey("13");
        assertThat(record.modifiedAt()).isEqualTo(Instant.ofEpochSecond(1762416000));
    }

    @Test
    void leadWithoutContactsKeepsItsOwnName() throws Exception {
        JsonNode lead = objectMapper.readTree("{ \"id\": 5, \"name\": \"Lead 5\", \"contacts\": [] }");

        CrmContactRecord record = UysotContactSyncAdapter.toRecord(5, lead, lead, Set.of());

        assertThat(record.name()).isEqualTo("Lead 5");
        assertThat(record.phones()).isEmpty();
        assertThat(record.modifiedAt()).isNull();
    }
}
