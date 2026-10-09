package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallReanalysisJobEntity;

@Component
public class CallReanalysisJobEntityMapper {

    public CallReanalysisJobEntity toCallReanalysisJobEntity(CallReanalysisJob job) {
        CallReanalysisJobEntity entity = new CallReanalysisJobEntity();
        entity.setCompanyId(job.companyId());
        entity.setAiAgentId(job.aiAgentId());
        entity.setRangeFrom(job.from());
        entity.setRangeTo(job.to());
        entity.setStatus(job.status());
        entity.setTotalCalls(job.totalCalls());
        entity.setProcessedCalls(job.processedCalls());
        entity.setUpdatedCalls(job.updatedCalls());
        entity.setFailedCalls(job.failedCalls());
        entity.setPromptTokens(job.promptTokens());
        entity.setCompletionTokens(job.completionTokens());
        entity.setChargedUzs(job.chargedUzs());
        entity.setLastCallId(job.lastCallId());
        entity.setLastError(job.lastError());
        entity.setStartedAt(job.startedAt());
        entity.setFinishedAt(job.finishedAt());
        return entity;
    }

    public CallReanalysisJob toCallReanalysisJob(CallReanalysisJobEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CallReanalysisJob(
                entity.getId(),
                entity.getCompanyId(),
                entity.getAiAgentId(),
                entity.getRangeFrom(),
                entity.getRangeTo(),
                entity.getStatus(),
                entity.getTotalCalls(),
                entity.getProcessedCalls(),
                entity.getUpdatedCalls(),
                entity.getFailedCalls(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getChargedUzs(),
                entity.getLastCallId(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt());
    }
}
