package uz.murodjon.robotcallv2.memory.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How much is kept about one client ({@code config/misc.yml}, {@code voice-agent.memory}).
 *
 * @param keptCalls how many past conversations are stored, newest first. Only the newest few reach the
 *                  prompt ({@code CallContext}); the rest are there for the operator's timeline
 * @param maxFacts  how many remembered facts are stored; past it the oldest one not typed by an operator goes
 */
@ConfigurationProperties(prefix = "voice-agent.memory")
public record ClientMemoryProperties(int keptCalls, int maxFacts) {

    public ClientMemoryProperties {
        if (keptCalls <= 0) {
            keptCalls = 10;
        }
        if (maxFacts <= 0) {
            maxFacts = 50;
        }
    }
}
