package uz.murodjon.robotcallv2.profile.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.UserSessionRow;

import java.util.List;

public interface ProfileSessionUseCase {

    List<UserSessionRow> list();

    void revoke(long companyId, long id);
}
