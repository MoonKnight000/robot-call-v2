package uz.murodjon.robotcallv2.callrecord.domain.enums;

/** What a {@code CallReanalysisJob} does with each call. */
public enum CallReanalysisMode {
    /** The agent's current analysis replaces the analysis keys of the stored outcome. Paid. */
    ANALYSIS,
    /**
     * Contact backfill from what each call already extracted: the stored outcome goes to the
     * caller's contact through the company's contact fields. No model request — free, except
     * an {@code ACCUMULATE} field's merge.
     */
    CONTACTS_STORED,
    /** {@link #ANALYSIS}, then the new outcome goes to the caller's contact. Paid. */
    CONTACTS_REEXTRACT;

    /** Whether each call is sent to the model again. */
    public boolean isReextracting() {
        return this != CONTACTS_STORED;
    }

    /** Whether each call's outcome is written to the caller's contact. */
    public boolean isWritingContacts() {
        return this != ANALYSIS;
    }
}
