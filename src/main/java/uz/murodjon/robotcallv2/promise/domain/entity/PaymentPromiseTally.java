package uz.murodjon.robotcallv2.promise.domain.entity;

/** How one number's promises to pay ended over a period; open and cancelled ones are not counted. */
public record PaymentPromiseTally(int kept, int broken) {

    public static final PaymentPromiseTally NONE = new PaymentPromiseTally(0, 0);
}
