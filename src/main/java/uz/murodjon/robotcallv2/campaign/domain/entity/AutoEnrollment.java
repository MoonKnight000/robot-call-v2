package uz.murodjon.robotcallv2.campaign.domain.entity;

import java.util.List;

/**
 * The rules that decide which of the company's contacts a campaign takes. An AUTO_ENROLL campaign adds every contact
 * that matches them, for as long as it runs; a STANDARD campaign takes the ones that match at launch, once (Retell's
 * "Filter contacts"), less the ones unticked in the list.
 *
 * @param matchAll           true when a contact must meet every rule, false when any one is enough
 * @param rules              no rules match nobody — never everybody
 * @param excludedContactIds STANDARD only: contacts that match but were unticked, so are not called
 */
public record AutoEnrollment(boolean matchAll, List<AutoEnrollRule> rules, List<Long> excludedContactIds) {

    public static final AutoEnrollment NONE = new AutoEnrollment(true, List.of(), List.of());

    public AutoEnrollment {
        rules = rules == null ? List.of() : List.copyOf(rules);
        excludedContactIds = excludedContactIds == null ? List.of() : List.copyOf(excludedContactIds);
    }

    public boolean hasNoRules() {
        return rules.isEmpty();
    }
}
