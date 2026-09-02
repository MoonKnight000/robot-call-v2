package uz.murodjon.robotcallv2.company.application.port.output;

import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.domain.enums.Language;

import java.time.LocalTime;
import java.util.List;

public interface CompanyConfigRepository {

    long create(long companyId, LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                Language defaultLanguage, List<Language> supportedLanguages, String disclosureText);

    CompanyConfig find(long companyId);

    void update(long companyId, LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                Language defaultLanguage, List<Language> supportedLanguages, String disclosureText);
}
