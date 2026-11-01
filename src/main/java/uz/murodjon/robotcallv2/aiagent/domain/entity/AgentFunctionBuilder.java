package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;

/**
 * Collects an {@link AgentFunction}'s fields; obtained from {@link AgentFunction#builder()} or, to change a
 * few fields of an existing function, {@link AgentFunction#toBuilder()}.
 */
public class AgentFunctionBuilder {

    private String id;
    private AgentFunctionType type;
    private String name;
    private String description;
    private boolean speakDuringExecution;
    private String executionMessage;
    private AgentFunctionTransfer transfer;
    private AgentFunctionAgentTransfer agentTransfer;
    private AgentFunctionPressDigit pressDigit;
    private AgentFunctionSms sms;
    private AgentFunctionExtract extract;
    private Long toolId;
    private String integrationTool;

    AgentFunctionBuilder() {
    }

    AgentFunctionBuilder(AgentFunction source) {
        this.id = source.id();
        this.type = source.type();
        this.name = source.name();
        this.description = source.description();
        this.speakDuringExecution = source.speakDuringExecution();
        this.executionMessage = source.executionMessage();
        this.transfer = source.transfer();
        this.agentTransfer = source.agentTransfer();
        this.pressDigit = source.pressDigit();
        this.sms = source.sms();
        this.extract = source.extract();
        this.toolId = source.toolId();
        this.integrationTool = source.integrationTool();
    }

    public AgentFunctionBuilder id(String id) {
        this.id = id;
        return this;
    }

    public AgentFunctionBuilder type(AgentFunctionType type) {
        this.type = type;
        return this;
    }

    public AgentFunctionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public AgentFunctionBuilder description(String description) {
        this.description = description;
        return this;
    }

    public AgentFunctionBuilder speakDuringExecution(boolean speakDuringExecution) {
        this.speakDuringExecution = speakDuringExecution;
        return this;
    }

    public AgentFunctionBuilder executionMessage(String executionMessage) {
        this.executionMessage = executionMessage;
        return this;
    }

    public AgentFunctionBuilder transfer(AgentFunctionTransfer transfer) {
        this.transfer = transfer;
        return this;
    }

    public AgentFunctionBuilder agentTransfer(AgentFunctionAgentTransfer agentTransfer) {
        this.agentTransfer = agentTransfer;
        return this;
    }

    public AgentFunctionBuilder pressDigit(AgentFunctionPressDigit pressDigit) {
        this.pressDigit = pressDigit;
        return this;
    }

    public AgentFunctionBuilder sms(AgentFunctionSms sms) {
        this.sms = sms;
        return this;
    }

    public AgentFunctionBuilder extract(AgentFunctionExtract extract) {
        this.extract = extract;
        return this;
    }

    public AgentFunctionBuilder toolId(Long toolId) {
        this.toolId = toolId;
        return this;
    }

    public AgentFunctionBuilder integrationTool(String integrationTool) {
        this.integrationTool = integrationTool;
        return this;
    }

    public AgentFunction build() {
        return new AgentFunction(id, type, name, description, speakDuringExecution, executionMessage, transfer,
                agentTransfer, pressDigit, sms, extract, toolId, integrationTool);
    }
}
