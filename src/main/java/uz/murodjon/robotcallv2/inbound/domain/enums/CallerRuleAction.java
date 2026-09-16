package uz.murodjon.robotcallv2.inbound.domain.enums;

/** What an inbound caller rule does to a matching caller, before any flow runs. */
public enum CallerRuleAction {
    /** Hang up at once. */
    BLOCK_HANGUP,
    /** Reject as busy — the caller hears an ordinary busy tone. */
    BLOCK_BUSY,
    /** Answer, say the rule's {@code message}, hang up. */
    BLOCK_MESSAGE,
    /** Put first in every queue the call reaches, and take the flow's CALLER_ROUTER 'vip' exit. */
    VIP;

    public boolean blocks() {
        return this != VIP;
    }
}
