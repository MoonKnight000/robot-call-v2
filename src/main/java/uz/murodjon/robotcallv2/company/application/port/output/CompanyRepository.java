package uz.murodjon.robotcallv2.company.application.port.output;

import uz.murodjon.robotcallv2.company.application.dto.CompanyFilter;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;

import java.util.List;

public interface CompanyRepository {

    long create(String name);

    Company find(long id);

    void update(long id, String name, String address);

    void updateLogoFileId(long id, Long logoFileId);

    void updateStatus(long id, CompanyStatus status);

    List<Company> findAll();

    List<Company> findAll(CompanyFilter filter);

    long count(CompanyFilter filter);
}
