package uz.murodjon.robotcallv2.company.application.port.output;

import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.shared.api.PageableData;

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

    List<Company> findAllCompanies();

    /** One page of the filter's companies together with the total, from one list query and at most one count. */
    PageableData<Company> findPage(CompanyFilter filter);
}
