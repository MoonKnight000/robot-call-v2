package uz.murodjon.robotcallv2.quality.application.port.input;

import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.FinishedCall;
import uz.murodjon.robotcallv2.quality.domain.entity.ScoredAgent;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;

import java.time.Instant;
import java.util.List;

/** Scoring finished calls (UNIVERSAL-OPERATOR-PLAN D.1) — reached from the post-call pipeline. */
public interface CallQualityUseCase {

    /**
     * Has the call judged and stores the verdict with the signals read without an LLM, then
     * marks {@code call.earlierCallIds} as repeated. A row is written even when the judge did
     * not score the call: the signals stand on their own.
     *
     * @param scenario what the agent was supposed to be doing — the rules it is judged against
     */
    void recordCallQuality(long companyId, FinishedCall call, ScenarioDefinition scenario);

    /**
     * Every agent, of every company, whose calls were recorded since {@code since} — what the
     * daily insight job (D.3) walks. Across companies on purpose: it runs under no request,
     * and each company id then travels as an argument.
     */
    List<ScoredAgent> findAgentsScoredSince(Instant since);

    /** One agent's rows in that window — the signals a day's proposals are read from. */
    List<CallQuality> findByAgentSince(long companyId, long agentId, Instant since);
}
