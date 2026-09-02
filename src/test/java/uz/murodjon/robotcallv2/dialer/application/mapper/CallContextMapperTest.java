package uz.murodjon.robotcallv2.dialer.application.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CallContextMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final CallContext IMPORTED = new CallContext(Map.of(
            "clientName", "Aziz Karimov",
            "debtAmount", new BigDecimal("1500000"),
            "currency", "so'm",
            "dueDate", LocalDate.of(2026, 7, 1),
            "contractNumber", "UY-2026-00123"
    ), "kampaniya maqsadi");

    @Test
    void crmValuesWinOverTheImportedSnapshot() {
        CrmClientSnapshot crm = new CrmClientSnapshot("Aziz K.", new BigDecimal("900000"),
                "so'm", LocalDate.of(2026, 8, 1), "UY-2026-00123", "ru-RU");

        CallContext merged = CallContextMapper.merge(IMPORTED, crm);

        assertThat((BigDecimal) merged.fact("debtAmount")).isEqualByComparingTo("900000");
        assertThat(merged.fact("clientName")).isEqualTo("Aziz K.");
        assertThat(merged.fact("dueDate")).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void fieldsTheCrmOmitsKeepTheImportedValue() {
        CrmClientSnapshot sparse = new CrmClientSnapshot(null, null, null, null, null, "ru-RU");

        CallContext merged = CallContextMapper.merge(IMPORTED, sparse);

        assertThat(merged.fact("clientName")).isEqualTo("Aziz Karimov");
        assertThat((BigDecimal) merged.fact("debtAmount")).isEqualByComparingTo("1500000");
        assertThat(merged.fact("contractNumber")).isEqualTo("UY-2026-00123");
    }

    @Test
    void theGoalAlwaysStaysWithTheCampaign() {
        CrmClientSnapshot crm = new CrmClientSnapshot("X", null, null, null, null, null);

        assertThat(CallContextMapper.merge(IMPORTED, crm).goal()).isEqualTo("kampaniya maqsadi");
    }

    @Test
    void aFailedLookupLeavesTheContextUntouched() {
        assertThat(CallContextMapper.merge(IMPORTED, null)).isEqualTo(IMPORTED);
    }

    @Test
    void readsBothCamelCaseAndSnakeCaseFromTheCrm() throws Exception {
        CrmClientSnapshot camel = CrmClientSnapshot.fromJson(JSON.readTree("""
                {"clientName":"Aziz","debtAmount":"1500000","dueDate":"2026-07-01",
                 "contractNumber":"UY-1","preferredLanguage":"uz-UZ"}"""));
        assertThat(camel.name()).isEqualTo("Aziz");
        assertThat(camel.debtAmount()).isEqualByComparingTo("1500000");
        assertThat(camel.preferredLanguage()).isEqualTo("uz-UZ");

        CrmClientSnapshot snake = CrmClientSnapshot.fromJson(JSON.readTree("""
                {"full_name":"Aziz","debt_amount":1500000,"due_date":"2026-07-01",
                 "contract_number":"UY-1","preferred_language":"ru-RU"}"""));
        assertThat(snake.name()).isEqualTo("Aziz");
        assertThat(snake.debtAmount()).isEqualByComparingTo("1500000");
        assertThat(snake.preferredLanguage()).isEqualTo("ru-RU");
    }

    @Test
    void unwrapsAWrappedEntity() throws Exception {
        CrmClientSnapshot wrapped = CrmClientSnapshot.fromJson(JSON.readTree(
                "{\"data\":{\"name\":\"Aziz\",\"debtAmount\":\"500\"}}"));

        assertThat(wrapped.name()).isEqualTo("Aziz");
        assertThat(wrapped.debtAmount()).isEqualByComparingTo("500");
    }

    @Test
    void toleratesATimestampWhereADateWasExpected() throws Exception {
        CrmClientSnapshot snapshot = CrmClientSnapshot.fromJson(JSON.readTree(
                "{\"dueDate\":\"2026-07-01T00:00:00Z\"}"));

        assertThat(snapshot.dueDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void unusableValuesBecomeNullRatherThanFailingTheCall() {
        CrmClientSnapshot snapshot = assertDoesNotThrowJson(
                "{\"debtAmount\":\"about a million\",\"dueDate\":\"soon\"}");

        assertThat(snapshot.debtAmount()).isNull();
        assertThat(snapshot.dueDate()).isNull();
    }

    @Test
    void handlesAnEmptyBody() throws Exception {
        CrmClientSnapshot snapshot = CrmClientSnapshot.fromJson(JSON.readTree("{}"));

        assertThat(snapshot.name()).isNull();
        assertThat(CrmClientSnapshot.fromJson(null).debtAmount()).isNull();
    }

    private static CrmClientSnapshot assertDoesNotThrowJson(String json) {
        try {
            return CrmClientSnapshot.fromJson(JSON.readTree(json));
        } catch (Exception e) {
            throw new AssertionError("parsing must not throw", e);
        }
    }
}

