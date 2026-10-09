package uz.murodjon.robotcallv2.pack.application.port.input;

import uz.murodjon.robotcallv2.pack.application.dto.*;
import uz.murodjon.robotcallv2.pack.domain.entity.VerticalPack;

import java.util.List;

/** Industry packs (UNIVERSAL-OPERATOR-PLAN C.1): the catalog, and installing one into a company. */
public interface PackUseCase {

    /** The same for every company — a pack is shipped with the build, not owned by a tenant. */
    List<PackRow> findPacks();

    /**
     * Creates the pack's agent, its tools (attached to it), its test cases, its catalog feed
     * when {@code request.catalogUrl} is given, and its conversion goal when the company has
     * none under that key. Secrets are never filled; the answer lists the ones still missing.
     *
     * @param request null is the same as an empty request
     */
    PackInstallResponse installPack(long companyId, String key, InstallPackRequest request);

    /**
     * Writes a pack for a business that has no pack (UNIVERSAL-OPERATOR-PLAN E.1): the model
     * writes the agent, the stages, the guardrails, the result fields and the test cases, and
     * the tools are built from the API document by the same importer the tool screen uses (B.2).
     *
     * <p>Nothing is created. The draft is checked the way a saved scenario is before it is
     * returned, so what comes back is something {@link #installDraft} will accept — and what
     * the person reads is what would run.
     */
    VerticalPack generatePack(long companyId, GeneratePackRequest request);

    /** Installs a pack that is not in the catalog — a generated draft, as the person edited it. */
    PackInstallResponse installDraft(long companyId, InstallDraftRequest request);

    /**
     * An agent as a pack — the same JSON {@code installDraft} takes, so an agent can be kept in git
     * and installed into another company or environment. The scenario is the one the agent runs,
     * a shared scenario copied in; the tools are the ones attached now, with every secret they
     * read listed by key and never by value.
     *
     * @param version a published version, or null for the draft
     */
    VerticalPack exportAgent(long companyId, long agentId, Integer version);
}
