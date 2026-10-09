package uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.quality.domain.enums.CriterionAnswer;

/** JPA entity for call_scorecard_answer. */
@Entity
@Table(name = "call_scorecard_answer")
public class CallScorecardAnswerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "call_id")
    private CallScorecardEntity scorecard;

    @Column(name = "criterion_id")
    private Long criterionId;

    @Column(name = "criterion_name", nullable = false)
    private String criterionName;

    @Column(nullable = false)
    private int weight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CriterionAnswer answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "judged_answer", nullable = false)
    private CriterionAnswer judgedAnswer;

    @Column
    private String evidence;

    public Long getId() {
        return id;
    }

    public CallScorecardEntity getScorecard() {
        return scorecard;
    }

    public void setScorecard(CallScorecardEntity scorecard) {
        this.scorecard = scorecard;
    }

    public Long getCriterionId() {
        return criterionId;
    }

    public void setCriterionId(Long criterionId) {
        this.criterionId = criterionId;
    }

    public String getCriterionName() {
        return criterionName;
    }

    public void setCriterionName(String criterionName) {
        this.criterionName = criterionName;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public CriterionAnswer getAnswer() {
        return answer;
    }

    public void setAnswer(CriterionAnswer answer) {
        this.answer = answer;
    }

    public CriterionAnswer getJudgedAnswer() {
        return judgedAnswer;
    }

    public void setJudgedAnswer(CriterionAnswer judgedAnswer) {
        this.judgedAnswer = judgedAnswer;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }
}
