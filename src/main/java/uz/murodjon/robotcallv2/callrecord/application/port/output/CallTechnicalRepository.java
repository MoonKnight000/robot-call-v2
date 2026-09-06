package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTechnical;

public interface CallTechnicalRepository {

    /** @return false when the call the row belongs to no longer exists */
    boolean save(CallTechnical technical);
}
