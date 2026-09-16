package uz.murodjon.robotcallv2.aiagent.domain.event;

/**
 * Published inside the clone's transaction once the copy's row exists. Whatever other
 * features keep per agent — the tool links — is copied by them on hearing it, so the
 * agent feature does not have to know those features.
 */
public record AiAgentClonedEvent(long companyId, long sourceAgentId, long copyAgentId) {
}
