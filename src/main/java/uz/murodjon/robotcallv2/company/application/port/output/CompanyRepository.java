package uz.murodjon.robotcallv2.company.application.port.output;

import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;

import java.util.List;

public interface CompanyRepository {

    long create(Company company);

    /**
     * Inserts the tenant under a caller-chosen id, doing nothing if that id is taken.
     * Only the startup seed needs this — every other tenant gets its id from the sequence.
     */
    void createWithId(long id, String name);

    boolean existsById(long id);

    Company find(long id);

    void update(long id, Company company);

    void updateLogoFileId(long id, Long logoFileId);

    void updateStatus(long id, CompanyStatus status);

    List<Company> findAll();

    List<Company> findAll(CompanyFilter filter);

    long count(CompanyFilter filter);
}
