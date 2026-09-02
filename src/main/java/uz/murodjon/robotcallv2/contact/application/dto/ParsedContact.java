package uz.murodjon.robotcallv2.contact.application.dto;

public record ParsedContact(
        int line,
        String name,
        String phone,
        String address,
        String tags,
        String notes
) {
}
