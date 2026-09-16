package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.InitiationWebhookResult;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Map;

/** An agent's own webhooks: the initiation hook at call start and the post-call hook after it ends. */
public interface AgentWebhookUseCase {

    /**
     * Calls the agent's initiation webhook synchronously at call start. The company's system
     * can inject dynamic variables or override the first message; any failure answers
     * {@link InitiationWebhookResult#EMPTY} and the call goes on.
     */
    InitiationWebhookResult executeInitiation(long companyId, long callAttemptId, String channelId, String phone,
                                              AiAgent agent, Map<String, Object> initialVariables);

    /** Sends the agent's post-call webhook asynchronously; a failed delivery is retried, never thrown. */
    void executePostCall(long companyId, long callAttemptId, String phone, AiAgent agent, Disposition disposition,
                         Integer durationSeconds, String summary, String recordingUrl, Map<String, Object> outcome,
                         Object transcript);
}
