package uz.murodjon.robotcallv2.phonenumber.application.dto;

public record TestSipConnectionResponse(
        boolean connected,
        long roundTripMs,
        String status,
        String details
) {
}
