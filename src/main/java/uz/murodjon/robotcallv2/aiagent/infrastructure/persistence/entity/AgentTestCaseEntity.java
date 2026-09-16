package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "agent_test_case")
public class AgentTestCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "persona_prompt", nullable = false)
    private String personaPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts", nullable = false, columnDefinition = "jsonb")
    private String facts;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "expect_disposition", length = 40)
    private String expectDisposition;

    @Column(name = "expect_stage", length = 100)
    private String expectStage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "must_say", nullable = false, columnDefinition = "jsonb")
    private String mustSay;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "must_not_say", nullable = false, columnDefinition = "jsonb")
    private String mustNotSay;

    @Column(name = "max_turns", nullable = false)
    private int maxTurns;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPersonaPrompt() {
        return personaPrompt;
    }

    public void setPersonaPrompt(String personaPrompt) {
        this.personaPrompt = personaPrompt;
    }

    public String getFacts() {
        return facts;
    }

    public void setFacts(String facts) {
        this.facts = facts;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getExpectDisposition() {
        return expectDisposition;
    }

    public void setExpectDisposition(String expectDisposition) {
        this.expectDisposition = expectDisposition;
    }

    public String getExpectStage() {
        return expectStage;
    }

    public void setExpectStage(String expectStage) {
        this.expectStage = expectStage;
    }

    public String getMustSay() {
        return mustSay;
    }

    public void setMustSay(String mustSay) {
        this.mustSay = mustSay;
    }

    public String getMustNotSay() {
        return mustNotSay;
    }

    public void setMustNotSay(String mustNotSay) {
        this.mustNotSay = mustNotSay;
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
