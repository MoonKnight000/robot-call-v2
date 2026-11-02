package uz.murodjon.robotcallv2.sms.application.port.input;

import uz.murodjon.robotcallv2.sms.application.dto.ConnectEskizRequest;
import uz.murodjon.robotcallv2.sms.application.dto.EskizConnectionRow;

/** A company's own Eskiz account, which its SMS are sent from. */
public interface EskizConnectionUseCase {

    EskizConnectionRow findByCompanyId(long companyId);

    /** Checks the login with Eskiz, then saves it encrypted; a login Eskiz refuses is not saved. */
    EskizConnectionRow connect(long companyId, ConnectEskizRequest request);

    /** Forgets the account; the company's SMS then go through the platform's login, if it has one. */
    void disconnect(long companyId);
}
