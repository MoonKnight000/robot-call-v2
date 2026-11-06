package uz.murodjon.robotcallv2.contact.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateAction;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode.*;

class ContactFieldUpdatePolicyTest {

    @Test
    void blankIncomingNeverWrites() {
        assertThat(ContactFieldUpdatePolicy.decide(OVERWRITE, "old", " ")).isEqualTo(ContactFieldUpdateAction.SKIP);
        assertThat(ContactFieldUpdatePolicy.decide(ACCUMULATE, null, null)).isEqualTo(ContactFieldUpdateAction.SKIP);
    }

    @Test
    void overwriteReplacesUnlessEqual() {
        assertThat(ContactFieldUpdatePolicy.decide(OVERWRITE, "warm", "hot")).isEqualTo(ContactFieldUpdateAction.WRITE);
        assertThat(ContactFieldUpdatePolicy.decide(OVERWRITE, "hot", "hot")).isEqualTo(ContactFieldUpdateAction.SKIP);
        assertThat(ContactFieldUpdatePolicy.decide(null, null, "hot")).isEqualTo(ContactFieldUpdateAction.WRITE);
    }

    @Test
    void fillIfEmptyWritesOnce() {
        assertThat(ContactFieldUpdatePolicy.decide(FILL_IF_EMPTY, null, "Aziz")).isEqualTo(ContactFieldUpdateAction.WRITE);
        assertThat(ContactFieldUpdatePolicy.decide(FILL_IF_EMPTY, "", "Aziz")).isEqualTo(ContactFieldUpdateAction.WRITE);
        assertThat(ContactFieldUpdatePolicy.decide(FILL_IF_EMPTY, "Ali", "Aziz")).isEqualTo(ContactFieldUpdateAction.SKIP);
    }

    @Test
    void accumulateMergesOnlyNewText() {
        assertThat(ContactFieldUpdatePolicy.decide(ACCUMULATE, null, "a")).isEqualTo(ContactFieldUpdateAction.WRITE);
        assertThat(ContactFieldUpdatePolicy.decide(ACCUMULATE, "a\n\nb", "b")).isEqualTo(ContactFieldUpdateAction.SKIP);
        assertThat(ContactFieldUpdatePolicy.decide(ACCUMULATE, "a", "b")).isEqualTo(ContactFieldUpdateAction.MERGE);
    }

    @Test
    void appendBelowKeepsTheNewestTextWithinLimit() {
        assertThat(ContactFieldUpdatePolicy.appendBelow("old", "new", 100)).isEqualTo("old\n\nnew");
        String fitted = ContactFieldUpdatePolicy.appendBelow("x".repeat(20), "latest", 10);
        assertThat(fitted).hasSizeLessThanOrEqualTo(10).endsWith("latest");
    }
}
