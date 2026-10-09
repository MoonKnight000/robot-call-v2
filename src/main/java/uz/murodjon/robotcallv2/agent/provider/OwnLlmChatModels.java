package uz.murodjon.robotcallv2.agent.provider;

import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aimodel.domain.entity.OwnLlmEndpoint;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClientFactory;

import java.time.Duration;

/**
 * Chat models on companies' own OpenAI-compatible LLMs (gap analysis 3.14).
 *
 * <p>One is built per call (or per summary) rather than cached: building is cheap — the
 * connection pool underneath is shared — and reading the key afresh each time means a key
 * rotated in the company's secrets takes effect on the next call, with nothing to evict.
 */
@Component
public class OwnLlmChatModels {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** The ceiling; a turn's own deadline ({@code LlmTimeoutProperties}) is far shorter. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final SecretUseCase secretUseCase;
    private final PinnedOpenAiHttpClient httpClient =
            new PinnedOpenAiHttpClient(PublicHttpClientFactory.create(CONNECT_TIMEOUT, READ_TIMEOUT), CONNECT_TIMEOUT);

    public OwnLlmChatModels(SecretUseCase secretUseCase) {
        this.secretUseCase = secretUseCase;
    }

    /**
     * @throws ConflictException when the secret the endpoint names is gone or cannot be
     *                           decrypted — the caller must not fall back to the platform's model
     */
    public ChatModel createChatModel(long companyId, OwnLlmEndpoint endpoint) {
        String apiKey = secretUseCase.findDecryptedSecrets(companyId).get(endpoint.apiKeySecret());
        if (apiKey == null || apiKey.isBlank()) {
            throw new ConflictException(ErrorCode.OWN_LLM_API_KEY_MISSING, endpoint.apiKeySecret());
        }
        ClientOptions options = ClientOptions.builder()
                .httpClient(httpClient)
                .baseUrl(endpoint.baseUrl())
                .apiKey(apiKey)
                .timeout(READ_TIMEOUT)
                // The dialog retries on its own, inside its turn deadline.
                .maxRetries(0)
                .build();
        return OpenAiChatModel.builder()
                .openAiClient(new OpenAIClientImpl(options))
                .openAiClientAsync(new OpenAIClientAsyncImpl(options))
                .options(OpenAiChatOptions.builder().model(endpoint.model()).build())
                .build();
    }
}
