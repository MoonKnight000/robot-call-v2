package uz.murodjon.robotcallv2.company.domain.service;

import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.Set;

public final class CompanyValidator {

    private CompanyValidator() {
    }

    /** @throws ValidationException {@code LANGUAGE_CODE_INVALID} when code names none of the supported languages */
    public static Language parseLanguage(String code) {
        return Language.findByCode(code)
                .orElseThrow(() -> new ValidationException(ErrorCode.LANGUAGE_CODE_INVALID, code, Language.codes()));
    }

    /**
     * The BCP-47 code of {@code requested}, or of {@link Language#DEFAULT} when it is blank.
     *
     * @throws ValidationException {@code LANGUAGE_CODE_INVALID} when code names none of the supported languages
     */
    public static String resolveLanguageCode(String requested) {
        return requested == null || requested.isBlank() ? Language.DEFAULT.code() : parseLanguage(requested).code();
    }

    /** Only the products the platform sells can be switched off — {@link Feature#CORE} is the platform itself. */
    public static void validateDisabledFeatures(Set<Feature> disabledFeatures) {
        for (Feature feature : disabledFeatures) {
            if (!feature.switchable()) {
                throw new ValidationException(ErrorCode.COMPANY_FEATURE_NOT_SWITCHABLE, feature);
            }
        }
    }
}
