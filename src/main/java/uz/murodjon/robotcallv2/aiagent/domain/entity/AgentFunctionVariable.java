package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVariableType;

import java.util.List;

/**
 * One variable an EXTRACT_VARIABLE function reads out of the conversation.
 *
 * @param name        the variable's name, usable as {{name}} afterwards
 * @param type        what kind of value it is; null reads as {@link AgentVariableType#STRING}
 * @param description what to look for, as the model reads it
 * @param choices     the allowed values of an {@link AgentVariableType#ENUM}; empty otherwise
 * @param remember    whether the value is also kept in the client's memory under {@code name}, so the
 *                    next call or chat with the same number knows it (the flat the client liked)
 */
public record AgentFunctionVariable(String name, AgentVariableType type, String description, List<String> choices,
                                    boolean remember) {

    public AgentFunctionVariable {
        type = type == null ? AgentVariableType.STRING : type;
        choices = choices == null ? List.of() : List.copyOf(choices);
    }
}
