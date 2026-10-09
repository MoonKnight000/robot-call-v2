package uz.murodjon.robotcallv2.shared.cluster;

/**
 * Tells every other instance of the cluster that something shared has changed, so each can
 * redo its own part of it — each instance with its own Asterisk regenerates that Asterisk's
 * config from the database.
 *
 * <p>A signal carries no data, only its topic: the receiver reads what it needs from shared
 * state. Delivery is best effort — an instance that is down misses it, and catches up on
 * its own at startup.
 */
public interface ClusterSignal {

    /** To every other instance subscribed to {@code topic}; never to this one. */
    void send(String topic);

    /** {@code handler} runs, off the caller's thread, whenever another instance sends {@code topic}. */
    void subscribe(String topic, Runnable handler);
}
