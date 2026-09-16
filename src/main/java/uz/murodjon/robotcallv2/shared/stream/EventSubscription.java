package uz.murodjon.robotcallv2.shared.stream;

/** An open push subscription; the transport cancels it once its client is gone. */
@FunctionalInterface
public interface EventSubscription {

    /** Stops delivering to the subscription's sink. Safe to call more than once. */
    void cancel();
}
