package uz.murodjon.robotcallv2.memory.domain.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Builds a {@link ClientMemory}; defaults live in the record's compact constructor. */
public final class ClientMemoryBuilder {

    private long id;
    private long companyId;
    private String phone;
    private String preferredName;
    private String preferredLanguage;
    private String operatorNotes;
    private List<RememberedCall> recentCalls;
    private Map<String, Object> facts;
    private Map<String, MemorySource> factSources;
    private Instant updatedAt;

    ClientMemoryBuilder() {
    }

    ClientMemoryBuilder(ClientMemory memory) {
        this.id = memory.id();
        this.companyId = memory.companyId();
        this.phone = memory.phone();
        this.preferredName = memory.preferredName();
        this.preferredLanguage = memory.preferredLanguage();
        this.operatorNotes = memory.operatorNotes();
        this.recentCalls = memory.recentCalls();
        this.facts = memory.facts();
        this.factSources = memory.factSources();
        this.updatedAt = memory.updatedAt();
    }

    public ClientMemoryBuilder id(long id) {
        this.id = id;
        return this;
    }

    public ClientMemoryBuilder companyId(long companyId) {
        this.companyId = companyId;
        return this;
    }

    public ClientMemoryBuilder phone(String phone) {
        this.phone = phone;
        return this;
    }

    public ClientMemoryBuilder preferredName(String preferredName) {
        this.preferredName = preferredName;
        return this;
    }

    public ClientMemoryBuilder preferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
        return this;
    }

    public ClientMemoryBuilder operatorNotes(String operatorNotes) {
        this.operatorNotes = operatorNotes;
        return this;
    }

    public ClientMemoryBuilder recentCalls(List<RememberedCall> recentCalls) {
        this.recentCalls = recentCalls;
        return this;
    }

    public ClientMemoryBuilder facts(Map<String, Object> facts) {
        this.facts = facts;
        return this;
    }

    public ClientMemoryBuilder factSources(Map<String, MemorySource> factSources) {
        this.factSources = factSources;
        return this;
    }

    public ClientMemoryBuilder updatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public ClientMemory build() {
        return new ClientMemory(id, companyId, phone, preferredName, preferredLanguage, operatorNotes, recentCalls,
                facts, factSources, updatedAt);
    }
}
