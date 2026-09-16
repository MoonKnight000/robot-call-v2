package uz.murodjon.robotcallv2.operator.application.port.output;

import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;

import java.util.List;

public interface OperatorSipAccountRepository {

    void create(long companyId, OperatorSipAccount account);

    void update(long companyId, long userId, boolean webrtc, String forwardNumber);

    void updatePassword(long companyId, long userId, String passwordEnc);

    void delete(long companyId, long userId);

    OperatorSipAccount find(long companyId, long userId);

    OperatorSipAccount findByUserId(long userId);

    List<OperatorSipAccount> findByCompanyId(long companyId);

    /** Every company's accounts — the generated Asterisk file holds them all. */
    List<OperatorSipAccount> findAll();
}
