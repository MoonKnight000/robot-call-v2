package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalResult;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentEvalRun;

import java.util.List;

/** One evaluation run with every case's transcript and verdict. */
public record AgentEvalRunDetail(
        AgentEvalRun run,
        List<AgentEvalResult> results
) {
}
