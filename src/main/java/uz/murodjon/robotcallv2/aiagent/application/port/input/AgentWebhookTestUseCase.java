package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AgentWebhookTestRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentWebhookTestResponse;

/** The studio's webhook "Test" button: one sample payload, sent once, nothing kept. */
public interface AgentWebhookTestUseCase {

    AgentWebhookTestResponse testWebhook(long companyId, long id, AgentWebhookTestRequest request);
}
