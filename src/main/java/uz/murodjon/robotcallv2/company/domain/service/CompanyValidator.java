package uz.murodjon.robotcallv2.company.domain.service;

import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.shared.dialog.Disclosure;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

public final class CompanyValidator {

    private CompanyValidator() {
    }

    public static void validateLanguages(Language defaultLanguage, List<Language> supportedLanguages) {
        if (supportedLanguages == null || !supportedLanguages.contains(defaultLanguage)) {
            List<String> codes = supportedLanguages != null
                    ? supportedLanguages.stream().map(Language::code).toList()
                    : List.of();
            throw new ValidationException(ErrorCode.COMPANY_CONFIG_DEFAULT_LANGUAGE_NOT_IN_SUPPORTED,
                    defaultLanguage != null ? defaultLanguage.code() : null, codes);
        }
    }

    public static void validateDisclosure(String disclosureText) {
        if (disclosureText != null && !disclosureText.isBlank() && !Disclosure.discloses(disclosureText)) {
            throw new ValidationException(ErrorCode.COMPANY_CONFIG_DISCLOSURE_INCOMPLETE);
        }
    }
}
