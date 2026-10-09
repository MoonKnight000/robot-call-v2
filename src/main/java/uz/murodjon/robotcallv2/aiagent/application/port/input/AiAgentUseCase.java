package uz.murodjon.robotcallv2.aiagent.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.Collection;
import java.util.Map;

/**
 * The agent itself: its rows for the editor, and the lookups every other feature and the
 * call pipeline make. Versions, section settings and the template catalog have their own
 * use cases ({@link AiAgentVersionUseCase}, {@link AiAgentSettingsUseCase},
 * {@link AiAgentCatalogUseCase}).
 */
public interface AiAgentUseCase {

    AiAgentRow createAgent(long companyId, CreateAiAgentRequest request);

    AiAgentRow updateAgent(long companyId, long id, UpdateAiAgentRequest request);

    AiAgentRow findAgentRow(long companyId, long id);

    /** The agent exactly as it was published in {@code version}, in the same shape as {@link #findAgentRow}. */
    AiAgentRow findVersionRow(long companyId, long id, int version);

    PageableData<AiAgentRow> filterAgents(long companyId, AiAgentFilter filter);

    void deleteAgent(long companyId, long id);

    /** A new draft with the same configuration and tool links, named "… (nusxa)". */
    AiAgentRow cloneAgent(long companyId, long id);

    /** The agent as its editors see it — the draft row. Reached from every screen and every ownership check. */
    AiAgent requireAgent(long companyId, long id);

    /**
     * The agent a call runs under — the latest published snapshot, or the draft itself when
     * the agent was never published (NEXT-PLAN A.1). Reached from the dialer sweep, the
     * ARI event loop and the widget. The draft's on/off switch applies either way.
     */
    AiAgent requirePublishedAgent(long companyId, long id);

    /**
     * The agent a call with this caller runs under: the published version, or — while a rollout
     * runs — the candidate, for a staging number or a caller in its traffic share. The same
     * number always gets the same version. Used where a call starts; elsewhere the published one.
     *
     * @param phone the caller's or the dialled number in any format; null stays on the published version
     */
    AiAgent requireCallAgent(long companyId, long id, String phone);

    /** A specific published version for a test call, or the draft when {@code version} is null. */
    AiAgent requireAgentVersion(long companyId, long id, Integer version);

    AiAgent findAgent(long companyId, long id);

    Map<Long, String> findNamesByIds(Collection<Long> ids);

    /** Resolves or synthesizes the Scenario this agent executes for a call. */
    Scenario resolveScenario(long companyId, AiAgent agent);

    /**
     * Adds one guardrail sentence to the agent's draft script, or takes it away again
     * (UNIVERSAL-OPERATOR-PLAN D.3): what accepting a {@code PROMPT_FIX} does, and what the
     * evaluation gate undoes when the agent then fails its own test cases.
     *
     * <p>One sentence at a time rather than a new script, so the change can be reversed by
     * its own text. Everything else the script holds — the stages, the goal, the tools — is
     * left exactly as it was, which rewriting the scenario section would not do.
     *
     * <p>Adding a rule that is already there, or removing one that is not, changes nothing.
     *
     * @param add true to add the rule, false to remove it
     * @throws uz.murodjon.robotcallv2.shared.exception.ConflictException when the agent speaks
     *         a shared scenario: that script belongs to every agent pointing at it
     */
    void changeScenarioGuardrail(long companyId, long agentId, String rule, boolean add);
}
