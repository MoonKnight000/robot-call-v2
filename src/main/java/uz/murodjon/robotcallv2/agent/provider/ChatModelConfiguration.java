package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Makes {@link RoutingChatModel} the {@link ChatModel} every injection point receives. */
@Configuration
public class ChatModelConfiguration {

    /**
     * Without a key for any vendor the bean is absent (a {@code null} result), so
     * {@code ObjectProvider<ChatModel>.getIfAvailable()} stays {@code null} and the dialog
     * engine, summaries and the other model users switch themselves off as before.
     */
    @Bean
    @Primary
    public ChatModel routingChatModel(ChatModelRegistry chatModelRegistry) {
        ChatVendor defaultVendor = chatModelRegistry.findDefaultVendor();
        return defaultVendor == null ? null : new RoutingChatModel(chatModelRegistry, defaultVendor);
    }
}
