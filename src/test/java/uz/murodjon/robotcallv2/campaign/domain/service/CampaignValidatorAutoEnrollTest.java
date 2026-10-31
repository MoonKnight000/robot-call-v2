package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollRule;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.enums.AutoEnrollField;
import uz.murodjon.robotcallv2.campaign.domain.enums.AutoEnrollOperator;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignKind;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignValidatorAutoEnrollTest {

    private final CampaignValidator validator = new CampaignValidator();

    private static AutoEnrollment rules(AutoEnrollRule... rules) {
        return new AutoEnrollment(true, List.of(rules), List.of());
    }

    private static AutoEnrollRule tag(String value) {
        return new AutoEnrollRule(AutoEnrollField.TAG, AutoEnrollOperator.EQUALS, value);
    }

    @Test
    void anAutoEnrollCampaignAcceptsRulesAndAlsoNoRulesYet() {
        assertThatCode(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL, rules(tag("qarzdor"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL, AutoEnrollment.NONE))
                .doesNotThrowAnyException();
    }

    @Test
    void aStandardCampaignFiltersByRulesAndOnlyItExcludesContacts() {
        AutoEnrollment withExclusion = new AutoEnrollment(true, List.of(tag("qarzdor")), List.of(7L));

        assertThatCode(() -> validator.validateAutoEnroll(CampaignKind.STANDARD, withExclusion))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL, withExclusion))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void aRuleNeedsAValueToCompareWith() {
        assertThatThrownBy(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL, rules(tag(" "))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL,
                rules(new AutoEnrollRule(AutoEnrollField.PHONE, AutoEnrollOperator.STARTS_WITH, "abc"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void doNotCallTakesEqualsTrueOrFalseOnly() {
        assertThatCode(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL,
                rules(new AutoEnrollRule(AutoEnrollField.DO_NOT_CALL, AutoEnrollOperator.EQUALS, "false"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL,
                rules(new AutoEnrollRule(AutoEnrollField.DO_NOT_CALL, AutoEnrollOperator.EQUALS, "maybe"))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL,
                rules(new AutoEnrollRule(AutoEnrollField.DO_NOT_CALL, AutoEnrollOperator.CONTAINS, "true"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void isAnyOfIsForTagsOnly() {
        assertThatCode(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL,
                rules(new AutoEnrollRule(AutoEnrollField.TAG, AutoEnrollOperator.IS_ANY_OF, "qarzdor, vip"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL,
                rules(new AutoEnrollRule(AutoEnrollField.NAME, AutoEnrollOperator.IS_ANY_OF, "Ali, Vali"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void phoneDoNotCallAndTagsTakeOneRowEachButTextFieldsSeveral() {
        assertThatThrownBy(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL, rules(tag("a"), tag("b"))))
                .isInstanceOf(ValidationException.class);
        assertThatCode(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL, rules(
                new AutoEnrollRule(AutoEnrollField.NAME, AutoEnrollOperator.STARTS_WITH, "Ali"),
                new AutoEnrollRule(AutoEnrollField.NAME, AutoEnrollOperator.NOT_EQUALS, "Alisher"))))
                .doesNotThrowAnyException();
    }

    @Test
    void moreThanTenRulesAreRejected() {
        AutoEnrollRule[] many = IntStream.range(0, 11).mapToObj(i -> tag("t" + i)).toArray(AutoEnrollRule[]::new);

        assertThatThrownBy(() -> validator.validateAutoEnroll(CampaignKind.AUTO_ENROLL, rules(many)))
                .isInstanceOf(ValidationException.class);
    }
}
