package uz.murodjon.robotcallv2.wrapup.application.port.output;

import uz.murodjon.robotcallv2.wrapup.domain.entity.CallWrapUp;

public interface CallWrapUpRepository {

    /** Writes the call's wrap-up, replacing the one it had. */
    CallWrapUp upsert(long companyId, CallWrapUp wrapUp);
}
