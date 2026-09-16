package uz.murodjon.robotcallv2.inbound.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.enums.CallerRuleAction;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallerRuleMatcherTest {

    private static InboundCallerRule rule(long id, String prefix, CallerRuleAction action) {
        return new InboundCallerRule(id, 1, prefix, action, null, null, null);
    }

    private final List<InboundCallerRule> rules = List.of(
            rule(1, "99890", CallerRuleAction.BLOCK_BUSY),
            rule(2, "998901112233", CallerRuleAction.VIP),
            rule(3, InboundCallerRule.ANONYMOUS, CallerRuleAction.BLOCK_MESSAGE));

    @Test
    void theLongestPrefixWins() {
        assertThat(CallerRuleMatcher.findMatch(rules, "998901112233").id()).isEqualTo(2);
        assertThat(CallerRuleMatcher.findMatch(rules, "998905556677").id()).isEqualTo(1);
    }

    @Test
    void aPlusSignDoesNotMatter() {
        assertThat(CallerRuleMatcher.findMatch(rules, "+998901112233").id()).isEqualTo(2);
    }

    @Test
    void aHiddenNumberMatchesOnlyTheAnonymousRule() {
        assertThat(CallerRuleMatcher.findMatch(rules, null).id()).isEqualTo(3);
        assertThat(CallerRuleMatcher.findMatch(rules, "").id()).isEqualTo(3);
    }

    @Test
    void anUnlistedCallerMatchesNothing() {
        assertThat(CallerRuleMatcher.findMatch(rules, "998935550000")).isNull();
        assertThat(CallerRuleMatcher.findMatch(List.of(), "998901112233")).isNull();
    }
}
