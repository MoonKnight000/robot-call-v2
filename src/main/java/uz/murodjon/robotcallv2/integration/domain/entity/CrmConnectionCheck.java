package uz.murodjon.robotcallv2.integration.domain.entity;

/**
 * What one cheap authenticated read against a company's CRM account answered — whether the
 * saved address and credential still work.
 *
 * @param ok     whether the CRM accepted the credential
 * @param reason why it did not, in a few words ({@code HTTP 401}, {@code unreachable}); never
 *               a message from the CRM, which can quote the address or the credential;
 *               {@code null} when {@code ok}
 */
public record CrmConnectionCheck(boolean ok, String reason) {

    public static CrmConnectionCheck accepted() {
        return new CrmConnectionCheck(true, null);
    }

    public static CrmConnectionCheck refused(String reason) {
        return new CrmConnectionCheck(false, reason);
    }

    /** A 2xx is the CRM accepting the credential; any other status is named in the reason. */
    public static CrmConnectionCheck ofStatus(int status) {
        return status / 100 == 2 ? accepted() : refused("HTTP " + status);
    }

    /** The request never got an answer; only the failure's type is kept, as it can quote the address. */
    public static CrmConnectionCheck unreachable(Exception failure) {
        return refused("unreachable (" + failure.getClass().getSimpleName() + ")");
    }
}
