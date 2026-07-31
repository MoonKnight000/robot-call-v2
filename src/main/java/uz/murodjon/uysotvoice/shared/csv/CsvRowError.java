package uz.murodjon.uysotvoice.shared.csv;

/**
 * One CSV row that could not be used, and why. Shared by every importer so a partial
 * import reports its rejects the same way whatever was being imported.
 *
 * @param line    1-based line number in the file, so an error can be found
 * @param message why the row was rejected
 */
public record CsvRowError(int line, String message) {
}
