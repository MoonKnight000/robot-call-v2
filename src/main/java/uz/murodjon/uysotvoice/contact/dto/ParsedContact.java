package uz.murodjon.uysotvoice.contact.dto;

/**
 * A contact ready to be inserted.
 *
 * @param line 1-based line number in the file, so an error can be found
 */
public record ParsedContact(
        int line,
        String name,
        String phone,
        String address,
        String tags,
        String notes
) {
}
