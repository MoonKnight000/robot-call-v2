package uz.murodjon.robotcallv2.scenario.domain.entity;

import uz.murodjon.robotcallv2.scenario.domain.enums.TransferMode;

import java.util.List;
import java.util.Map;

/**
 * The settings of a stage that does one thing instead of talking ({@code StageType}). Each
 * type reads only its own fields; the rest stay null.
 *
 * @param toolName        FUNCTION: a tool of the scenario's {@code tools} or one of the company's tools attached
 *                        to the agent — not checked on save, a scenario does not know its agents' tools;
 *                        MCP: a tool of the connection
 * @param transferTo      CALL_TRANSFER: the number the caller is handed to
 * @param transferMode    CALL_TRANSFER: null = {@link TransferMode#COLD}
 * @param digits          PRESS_DIGIT: tones to send — {@code 0-9}, {@code *}, {@code #}, and {@code w}
 *                        for a half-second pause
 * @param targetAgentId   AGENT_TRANSFER: the AI agent the conversation is handed to
 * @param smsText         SMS: the message, {@code {{fact}}} templates filled in by the code
 * @param extractFields   EXTRACT_VARIABLE: names from the scenario's {@code outcomeSchema}
 * @param code            CODE: the script to run
 * @param mcpConnectionId MCP: the company's MCP connection {@code toolName} belongs to
 * @param sipHeaders      CALL_TRANSFER: custom headers on the INVITE of the transferred leg, name → value,
 *                        {@code {{fact}}} templates in values filled in by the code; names start with
 *                        {@code X-}; null or empty = none
 */
public record StageAction(
        String toolName,
        String transferTo,
        TransferMode transferMode,
        String digits,
        Long targetAgentId,
        String smsText,
        List<String> extractFields,
        String code,
        Long mcpConnectionId,
        Map<String, String> sipHeaders
) {
    public StageAction {
        if (transferMode == null) {
            transferMode = TransferMode.COLD;
        }
    }
}
