package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.models.Channel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an Asterisk channel says about itself: its id, whether it is one of our own
 * externalMedia legs, the trunk it came in on, the DID and the caller's number.
 */
final class ChannelNames {

    /** externalMedia channels appear in Stasis with this technology prefix. */
    private static final String EXTERNAL_MEDIA_PREFIX = "UnicastRTP";
    /** {@code PJSIP/<endpoint>-<seq>} -> group 1 is the endpoint (§10.5 "Texnik" tab trunk). */
    private static final Pattern TRUNK_FROM_CHANNEL_NAME = Pattern.compile("PJSIP/(.+)-[0-9a-fA-F]+");
    /** A managed trunk's endpoint, as SipTrunkService names it: trunk_&lt;companyId&gt;_&lt;id&gt;. */
    private static final Pattern MANAGED_TRUNK_ENDPOINT = Pattern.compile("trunk_(\\d+)_\\d+");

    private ChannelNames() {
    }

    static String extractChannelId(Channel channel) {
        return channel != null ? channel.getId() : null;
    }

    /** Whether {@code channelName} is an externalMedia leg this application created, not a caller. */
    static boolean isExternalMedia(String channelName) {
        return channelName != null && channelName.startsWith(EXTERNAL_MEDIA_PREFIX);
    }

    /**
     * A managed trunk's endpoint name carries its owner, so a call that came in on one can
     * only be for that company's routes — a route another tenant created for the same
     * number must not receive it. Endpoints that name no owner (the platform trunk, a
     * softphone) pass.
     */
    static boolean isTrunkAllowedFor(String channelName, long companyId) {
        String trunk = extractTrunk(channelName);
        Matcher managed = trunk != null ? MANAGED_TRUNK_ENDPOINT.matcher(trunk) : null;
        return managed == null || !managed.matches() || Long.parseLong(managed.group(1)) == companyId;
    }

    static String extractTrunk(String channelName) {
        if (channelName == null) {
            return null;
        }
        Matcher m = TRUNK_FROM_CHANNEL_NAME.matcher(channelName);
        return m.matches() ? m.group(1) : null;
    }

    static String extractDid(Channel channel) {
        if (channel != null && channel.getDialplan() != null && channel.getDialplan().getExten() != null
                && !channel.getDialplan().getExten().isBlank()) {
            return channel.getDialplan().getExten();
        }
        return channel != null ? extractTrunk(channel.getName()) : null;
    }

    static String extractCallerNumber(Channel channel) {
        if (channel == null || channel.getCaller() == null) {
            return null;
        }
        String number = channel.getCaller().getNumber();
        return number != null && !number.isBlank() ? number : null;
    }
}
