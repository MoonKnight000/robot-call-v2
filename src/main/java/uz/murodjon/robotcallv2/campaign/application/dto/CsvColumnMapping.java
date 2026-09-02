package uz.murodjon.robotcallv2.campaign.application.dto;

/**
 * One CSV header matched against the importer's known columns (§10.6 "ustunni
 * moslashtirish" wizard step).
 *
 * @param header       the raw header text as it appears in the file
 * @param mappedField  the target field it will be loaded into ({@code clientId}, {@code phone},
 *                     {@code language}, or a {@code context_data} key like {@code debtAmount}),
 *                     or {@code null} if the importer does not recognize this header
 */
public record CsvColumnMapping(String header, String mappedField) {
}
