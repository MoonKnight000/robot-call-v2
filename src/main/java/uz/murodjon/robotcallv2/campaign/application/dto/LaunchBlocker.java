package uz.murodjon.robotcallv2.campaign.application.dto;

/**
 * What stops a campaign from being launched right now.
 *
 * @param code    the {@code messageCode} the launch itself would answer with
 * @param message that error's text
 */
public record LaunchBlocker(String code, String message) {
}
