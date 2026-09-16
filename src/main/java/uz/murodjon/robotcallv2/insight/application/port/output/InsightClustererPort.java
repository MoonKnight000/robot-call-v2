package uz.murodjon.robotcallv2.insight.application.port.output;

import uz.murodjon.robotcallv2.insight.domain.entity.AgentCallSignals;
import uz.murodjon.robotcallv2.insight.domain.entity.InsightProposal;

import java.util.List;

/**
 * Turns a day of one agent's signals into the few changes worth proposing
 * (UNIVERSAL-OPERATOR-PLAN D.3).
 *
 * <p>A port rather than a call to a model, because what does the clustering is not the
 * service's business: today an LLM reads the counts and the notes, and a company without a
 * model configured gets an empty list and no insights rather than a broken daily job.
 *
 * @param agentName what the agent is called, so a proposal can name it the way a person would
 */
public interface InsightClustererPort {

    List<InsightProposal> cluster(String agentName, String language, AgentCallSignals signals);
}
