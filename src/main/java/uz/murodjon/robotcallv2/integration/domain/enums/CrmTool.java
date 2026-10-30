package uz.murodjon.robotcallv2.integration.domain.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * The tools whose handler is code: {@code integration_tool.tool_key} of a {@link IntegrationToolKind#BUILT_IN}
 * row is one of these names, and {@link #toolName} is the name the model calls — the method name
 * in {@code RealEstateTools} / {@code CrmLeadTools}.
 *
 * <p>Everything a person reads or the platform admin edits (label, description, grant, default)
 * lives in the {@code integration_tool} row, not here.
 */
public enum CrmTool {

    SEARCH_FLATS("searchFlats"),
    LIST_COMPLEXES("listResidentialComplexes"),
    REMEMBER_FLAT_INTEREST("rememberFlatInterest"),
    GET_CONTRACT_BALANCE("getContractBalance"),
    GET_PAYMENT_SCHEDULE("getPaymentSchedule"),
    GET_DEBT_SUMMARY("getDebtSummary"),
    GET_CLIENT_PROFILE("getClientProfile"),
    CREATE_LEAD("createLead"),
    ADD_LEAD_NOTE("addLeadNote"),
    CREATE_FOLLOW_UP_TASK("createFollowUpTask"),
    MOVE_LEAD_STAGE("moveLeadStage"),
    REQUEST_MANAGER_CALLBACK("requestManagerCallback"),
    GET_BOOKING_STATUS("getBookingStatus");

    private final String toolName;

    CrmTool(String toolName) {
        this.toolName = toolName;
    }

    public String toolName() {
        return toolName;
    }

    /** The built-in tool a catalog key names; empty for a key this build has no handler for. */
    public static Optional<CrmTool> findByKey(String key) {
        return Arrays.stream(values()).filter(tool -> tool.name().equals(key)).findFirst();
    }
}
