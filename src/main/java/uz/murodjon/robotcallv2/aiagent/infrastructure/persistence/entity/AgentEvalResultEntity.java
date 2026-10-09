package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "agent_eval_result")
public class AgentEvalResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "test_case_id")
    private Long testCaseId;

    @Column(name = "case_name", nullable = false, length = 200)
    private String caseName;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "disposition", length = 40)
    private String disposition;

    @Column(name = "stage", length = 100)
    private String stage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "problems", nullable = false, columnDefinition = "jsonb")
    private String problems;

    @Column(name = "transcript", nullable = false)
    private String transcript;

    @Column(name = "qa_score")
    private Integer qaScore;

    @Column(name = "guardrail_violation", nullable = false)
    private boolean guardrailViolation;

    @Column(name = "attempt", nullable = false)
    private int attempt = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public Long getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(Long testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getProblems() {
        return problems;
    }

    public void setProblems(String problems) {
        this.problems = problems;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public Integer getQaScore() {
        return qaScore;
    }

    public void setQaScore(Integer qaScore) {
        this.qaScore = qaScore;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public boolean isGuardrailViolation() {
        return guardrailViolation;
    }

    public void setGuardrailViolation(boolean guardrailViolation) {
        this.guardrailViolation = guardrailViolation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
