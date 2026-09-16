package uz.murodjon.robotcallv2.callflow.domain.enums;

import java.util.EnumSet;
import java.util.Set;

import static uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowExit.*;

/**
 * What a call-flow node does (docs/INBOUND-PBX-PLAN.md §4.2), and the exits it may take.
 * An exit a node does not name ends the call.
 */
public enum CallFlowNodeType {
    /** Say {@code prompt} (or play {@code audioFileId}). */
    PLAY(EnumSet.of(NEXT)),
    /** Branch on {@code scheduleId}: open, closed, or a holiday. */
    SCHEDULE(EnumSet.of(OPEN, CLOSED, HOLIDAY)),
    /** Keypad (and optionally spoken) menu; {@code options} maps a key to the next node. */
    MENU(EnumSet.of(TIMEOUT, INVALID)),
    /** "O'zbek tili uchun 1 ni bosing": {@code options} maps a key to a language tag. */
    LANGUAGE_SELECT(EnumSet.of(NEXT)),
    /** Hand the call to the AI agent {@code aiAgentId}. */
    AI_AGENT(EnumSet.of(COMPLETED, TRANSFER)),
    /** Wait in operator queue {@code queueId}. */
    QUEUE(EnumSet.of(ANSWERED, OVERFLOW, CALLBACK)),
    /** Ring {@code userIds} at once. */
    RING_GROUP(EnumSet.of(ANSWERED, NO_ANSWER)),
    /** Ring the one operator in {@code userIds}. */
    EXTENSION(EnumSet.of(ANSWERED, NO_ANSWER)),
    /** Ring {@code phoneNumber} over the trunk. */
    FORWARD_EXTERNAL(EnumSet.of(ANSWERED, NO_ANSWER)),
    /** Record a message into {@code voicemailBoxId}. */
    VOICEMAIL(EnumSet.of(NEXT)),
    /** "Qayta qo'ng'iroq qilaylikmi? 1 ni bosing." */
    CALLBACK_OFFER(EnumSet.of(ACCEPTED, DECLINED)),
    /** Branch on who is calling: a VIP rule, a caller the CRM knows, a repeat caller. */
    CALLER_ROUTER(EnumSet.of(VIP, KNOWN, REPEAT, DEFAULT)),
    /** Ask {@code url} which exit to take; any exit name it answers with is allowed. */
    WEBHOOK_ROUTER(EnumSet.of(DEFAULT, ERROR)),
    HANGUP(EnumSet.noneOf(CallFlowExit.class));

    private final Set<CallFlowExit> exits;

    CallFlowNodeType(Set<CallFlowExit> exits) {
        this.exits = exits;
    }

    public Set<CallFlowExit> exits() {
        return exits;
    }

    /** Whether {@code key} is one of this type's exits (WEBHOOK_ROUTER takes any name). */
    public boolean allowsExit(String key) {
        if (this == WEBHOOK_ROUTER) {
            return key != null && !key.isBlank();
        }
        return exits.stream().anyMatch(exit -> exit.key().equals(key));
    }
}
