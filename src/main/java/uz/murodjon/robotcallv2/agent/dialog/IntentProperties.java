package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for real-time intent detection — bound from {@code voice-agent.intent.*}.
 *
 * @param enabled when true, caller utterances are classified into intent categories
 */
@ConfigurationProperties(prefix = "voice-agent.intent")
public record IntentProperties(
        @DefaultValue("true") boolean enabled
) {

    @ConstructorBinding
    public IntentProperties(boolean enabled) {
        this.enabled = enabled;
    }

    public IntentProperties() {
        this(true);
    }
}
