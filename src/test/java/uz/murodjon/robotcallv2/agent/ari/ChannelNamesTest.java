package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.models.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The trunk in a channel's name decides which company's routes an inbound call may reach —
 * a wrong answer here hands one tenant's caller to another tenant's agent.
 */
class ChannelNamesTest {

    @ParameterizedTest
    @CsvSource({
            "PJSIP/trunk_5_12-0000001a, trunk_5_12",
            "PJSIP/skyline-00000003,    skyline",
            "PJSIP/op-7-0000ABCD,       op-7",
    })
    void extractsTheEndpointFromAPjsipChannelName(String channelName, String trunk) {
        assertThat(ChannelNames.extractTrunk(channelName)).isEqualTo(trunk);
    }

    @Test
    void aNameOfAnotherShapeHasNoTrunk() {
        assertThat(ChannelNames.extractTrunk(null)).isNull();
        assertThat(ChannelNames.extractTrunk("Local/600@from-internal-00000001;1")).isNull();
        assertThat(ChannelNames.extractTrunk("UnicastRTP/127.0.0.1:10000-0x7f5c")).isNull();
    }

    @Test
    void aManagedTrunkCarriesOnlyItsOwnersCalls() {
        assertThat(ChannelNames.isTrunkAllowedFor("PJSIP/trunk_5_12-0000001a", 5)).isTrue();
        assertThat(ChannelNames.isTrunkAllowedFor("PJSIP/trunk_5_12-0000001a", 6)).isFalse();
    }

    @Test
    void anEndpointThatNamesNoOwnerCarriesAnyCompany() {
        // The platform trunk and a softphone: nothing in the name ties them to a tenant.
        assertThat(ChannelNames.isTrunkAllowedFor("PJSIP/skyline-0000001a", 5)).isTrue();
        assertThat(ChannelNames.isTrunkAllowedFor("PJSIP/skyline-0000001a", 6)).isTrue();
        assertThat(ChannelNames.isTrunkAllowedFor("Local/600@from-internal-00000001;1", 5)).isTrue();
        assertThat(ChannelNames.isTrunkAllowedFor(null, 5)).isTrue();
    }

    @Test
    void recognisesItsOwnExternalMediaLegs() {
        assertThat(ChannelNames.isExternalMedia("UnicastRTP/127.0.0.1:10000-0x7f5c")).isTrue();
        assertThat(ChannelNames.isExternalMedia("PJSIP/trunk_5_12-0000001a")).isFalse();
        assertThat(ChannelNames.isExternalMedia(null)).isFalse();
    }

    @Test
    void theChannelIdIsTheChannelsOwn() {
        Channel channel = mock(Channel.class);
        when(channel.getId()).thenReturn("1757.42");

        assertThat(ChannelNames.extractChannelId(channel)).isEqualTo("1757.42");
        assertThat(ChannelNames.extractChannelId(null)).isNull();
    }

    @Test
    void theCallerNumberIsTheCallerIdWhenThereIsOne() {
        Channel known = mock(Channel.class, RETURNS_DEEP_STUBS);
        when(known.getCaller().getNumber()).thenReturn("998901234567");
        Channel blank = mock(Channel.class, RETURNS_DEEP_STUBS);
        when(blank.getCaller().getNumber()).thenReturn("  ");
        Channel withoutCaller = mock(Channel.class);

        assertThat(ChannelNames.extractCallerNumber(known)).isEqualTo("998901234567");
        assertThat(ChannelNames.extractCallerNumber(blank)).isNull();
        assertThat(ChannelNames.extractCallerNumber(withoutCaller)).isNull();
        assertThat(ChannelNames.extractCallerNumber(null)).isNull();
    }

    @Test
    void theDidIsTheDialledExtensionElseTheTrunk() {
        Channel dialled = mock(Channel.class, RETURNS_DEEP_STUBS);
        when(dialled.getDialplan().getExten()).thenReturn("712000000");
        Channel noExtension = mock(Channel.class, RETURNS_DEEP_STUBS);
        when(noExtension.getDialplan().getExten()).thenReturn(" ");
        when(noExtension.getName()).thenReturn("PJSIP/skyline-0000001a");

        assertThat(ChannelNames.extractDid(dialled)).isEqualTo("712000000");
        assertThat(ChannelNames.extractDid(noExtension)).isEqualTo("skyline");
        assertThat(ChannelNames.extractDid(null)).isNull();
    }
}
