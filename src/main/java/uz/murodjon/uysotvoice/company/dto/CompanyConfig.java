package uz.murodjon.uysotvoice.company.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import uz.murodjon.uysotvoice.company.enums.Language;
import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * A company's settings ({@code GET/PUT /api/companies/{id}/config}, ROADMAP B.1/B.3).
 *
 * @param defaultLanguage    used when a campaign/route is created without picking one
 *                           explicitly (backend-uchun-talablar.md §13); always a member
 *                           of {@code supportedLanguages}
 * @param supportedLanguages every language a campaign or inbound route may declare,
 *                           {@code defaultLanguage} included — a closed set (report #6),
 *                           no longer free-text
 * @param dialWindowStart    strict ceiling on top of every campaign's own window
 * @param dialWindowEnd      strict ceiling on top of every campaign's own window
 * @param disclosureText     the §11.1 notice spoken at the start of this company's
 *                           calls; {@code {company}} is replaced with the company name.
 *                           Blank falls back to the platform's own wording, and a
 *                           scenario may override it — see {@code shared.dialog.Disclosure}
 */
public record CompanyConfig(
        long id,
        long companyId,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        String timezone,
        Language defaultLanguage,
        List<Language> supportedLanguages,
        String disclosureText,
        Instant createdAt
) {
}
