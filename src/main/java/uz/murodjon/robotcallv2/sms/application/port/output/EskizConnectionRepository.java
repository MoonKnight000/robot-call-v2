package uz.murodjon.robotcallv2.sms.application.port.output;

import uz.murodjon.robotcallv2.sms.domain.entity.EskizConnection;

import java.util.Optional;

public interface EskizConnectionRepository {

    /** The company's own Eskiz account; empty when it has not connected one. */
    Optional<EskizConnection> findByCompanyId(long companyId);

    /** Saves the company's account, replacing the one it had. */
    EskizConnection upsert(long companyId, EskizConnection connection);

    /** Removes the company's account; nothing happens when it has none. */
    void deleteByCompanyId(long companyId);
}
