package uz.murodjon.uysotvoice.company.dto;

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
 *                           {@code defaultLanguage} included
 * @param dialWindowStart    strict ceiling on top of every campaign's own window
 * @param dialWindowEnd      strict ceiling on top of every campaign's own window
 */
public record CompanyConfig(
        long id,
        long companyId,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd,
        String timezone,
        String defaultLanguage,
        List<String> supportedLanguages,
        Instant createdAt
) {
}
