package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * The call a CONVERSATION step places.
 *
 * @param aiAgentId         the agent that speaks on this step's calls
 * @param maxAttempts       calls a contact gets on this step before it is routed as NO_RESPONSE
 * @param retryDelayMinutes minutes between two attempts of the same step; 0 leaves it to the
 *                          campaign's per-disposition defaults
 */
public record ConversationStepConfig(Long aiAgentId, int maxAttempts, int retryDelayMinutes) {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final int MAX_ATTEMPTS = 10;

    public ConversationStepConfig {
        maxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
        retryDelayMinutes = Math.max(0, retryDelayMinutes);
    }
}
