package uz.murodjon.robotcallv2.billing.application.dto;

/** An error text Payme shows the payer, in the language Payme picks. */
public record PaymeErrorMessage(
        String ru,
        String uz,
        String en
) {
}
