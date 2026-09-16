package uz.murodjon.robotcallv2.callflow.domain.enums;

/**
 * The named ways out of a call-flow node. A node's {@code exits} map is keyed by
 * {@link #key()}; which keys a node may use is {@link CallFlowNodeType#exits()}.
 */
public enum CallFlowExit {
    NEXT("next"),
    OPEN("open"),
    CLOSED("closed"),
    HOLIDAY("holiday"),
    TIMEOUT("timeout"),
    INVALID("invalid"),
    COMPLETED("completed"),
    TRANSFER("transfer"),
    ANSWERED("answered"),
    OVERFLOW("overflow"),
    CALLBACK("callback"),
    NO_ANSWER("no_answer"),
    ACCEPTED("accepted"),
    DECLINED("declined"),
    VIP("vip"),
    KNOWN("known"),
    REPEAT("repeat"),
    DEFAULT("default"),
    ERROR("error");

    private final String key;

    CallFlowExit(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
