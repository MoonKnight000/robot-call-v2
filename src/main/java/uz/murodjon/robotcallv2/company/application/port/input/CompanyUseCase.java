package uz.murodjon.robotcallv2.company.application.port.input;

import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyFilter;
import uz.murodjon.robotcallv2.company.application.dto.CreateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyRequest;
import uz.murodjon.robotcallv2.company.application.dto.UpdateCompanyStatusRequest;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface CompanyUseCase {

    Company create(CreateCompanyRequest r);

    Company update(long callerCompanyId, long id, UpdateCompanyRequest request);

    Company updateStatus(long id, UpdateCompanyStatusRequest r);

    Company uploadLogo(long callerCompanyId, long id, MultipartFile file);

    PageableData<Company> list(CompanyFilter filter);

    Company findById(long id);

    List<Company> findAllForWarmup();

    Company requireCompany(long callerCompanyId, long id);
}
