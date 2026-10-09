package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalResult;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;

import java.util.List;

/**
 * One evaluation run with every attempt's transcript and verdict, and each case's pass rate
 * over its attempts.
 */
public record AgentEvalRunDetail(
        AgentEvalRun run,
        List<AgentEvalCaseSummary> cases,
        List<AgentEvalResult> results
) {
}
