package uz.murodjon.robotcallv2.shared.dialog;

/**
 * Which language a call is held in, from what is known about this particular client down
 * to what was configured for everyone.
 *
 * <p>Both directions ask the same question and must answer it the same way: a client the
 * company knows to be Russian-speaking is spoken to in Russian whether the dialer called
 * them ({@code CallTaskConsumer}) or they called in ({@code AriService}). The rule lived
 * in both places, and the inbound copy was the one that had already drifted — it read the
 * agent's language before the CRM and the memory were even loaded.
 *
 * <p>Takes plain strings rather than the CRM and memory records so this package stays
 * independent of the features that produce them (CLAUDE.md §2 {@code shared/}).
 */
public final class CallLanguage {

    private CallLanguage() {
    }

    /**
     * The call's BCP-47 language.
     *
     * @param crmLanguage    what the CRM holds for this client, most authoritative because
     *                       it is the company's own record and is edited outside this system
     * @param memoryLanguage what an earlier call or an operator established
     *                       ({@code client_memory.preferred_language})
     * @param fallback       what to speak when nothing is known about this client: the
     *                       target's or campaign's language outbound, the answering agent's
     *                       language inbound
     */
    public static String resolve(String crmLanguage, String memoryLanguage, String fallback) {
        if (isSet(crmLanguage)) {
            return crmLanguage;
        }
        if (isSet(memoryLanguage)) {
            return memoryLanguage;
        }
        return fallback;
    }

    private static boolean isSet(String language) {
        return language != null && !language.isBlank();
    }
}
