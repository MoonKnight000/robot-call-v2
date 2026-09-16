package uz.murodjon.robotcallv2.quality.application.port.input;

import uz.murodjon.robotcallv2.aiagent.application.dto.SaveAgentTestCaseRequest;
import uz.murodjon.robotcallv2.knowledgebase.application.dto.KnowledgeItemCreateRequest;
import uz.murodjon.robotcallv2.quality.application.dto.ReviewCallRequest;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.CallReviewFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/** The review inbox (UNIVERSAL-OPERATOR-PLAN D.2): a person's verdict on the calls the judge flagged. */
public interface CallReviewUseCase {

    PageableData<CallQuality> filterReviews(long companyId, CallReviewFilter filter);

    CallQuality findReview(long companyId, long callAttemptId);

    /** Any scored call may be reviewed, flagged or not — an unflagged bad call is the judge's miss. */
    CallQuality recordReview(long companyId, long callAttemptId, ReviewCallRequest request);

    /** A knowledge base answer for the call's agent, written from what this call lacked. */
    CallQuality createKnowledgeItem(long companyId, long callAttemptId, KnowledgeItemCreateRequest request);

    /** A test case on the call's agent, so the eval replays what went wrong here. */
    CallQuality createTestCase(long companyId, long callAttemptId, SaveAgentTestCaseRequest request);
}
