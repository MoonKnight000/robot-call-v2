package uz.murodjon.robotcallv2.shared.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * The subscriber registry behind a server-push stream. Every subscription is bound to an
 * audience — whose events it may receive — and has its own bounded queue and its own virtual
 * thread that does the blocking write.
 *
 * <p>Publishing only offers the event to the matching queues. Events come from media, ARI and
 * call-queue threads, and writing inline let one client on a bad network (a full TCP window)
 * stall every one of them. A subscription that falls {@value #QUEUE_CAPACITY} events behind
 * loses the newest.
 *
 * <p>Not a bean: the service behind each stream owns one and drives its heartbeat.
 *
 * @param <A> what a subscription is bound to — a company id, a company and user, …
 */
public final class EventStreamHub<A> {

    private static final Logger log = LoggerFactory.getLogger(EventStreamHub.class);
    /** Events one slow client may fall behind by before its newest ones are dropped. */
    private static final int QUEUE_CAPACITY = 256;
    /** The first event of every subscription; its payload is the subscription id. */
    private static final String CONNECTED_EVENT = "connected";

    /** One queued delivery. */
    private record Delivery(String eventName, Object payload) {
    }

    /** Compared by identity: the one delivery that is a heartbeat, not an event. */
    private static final Delivery HEARTBEAT = new Delivery(null, null);

    private static final class Subscriber<A> {
        private final A audience;
        private final EventSink sink;
        private final BlockingQueue<Delivery> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        private volatile Thread writer;

        private Subscriber(A audience, EventSink sink) {
            this.audience = audience;
            this.sink = sink;
        }
    }

    private final String name;
    private final Map<Long, Subscriber<A>> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    /** @param name names the writer threads ({@code <name>-<subscriptionId>}) and the log lines */
    public EventStreamHub(String name) {
        this.name = name;
    }

    /** Opens a subscription: {@code connected} first, then every event {@code audience} is sent. */
    public EventSubscription subscribe(A audience, EventSink sink) {
        long id = nextId.incrementAndGet();
        Subscriber<A> subscriber = new Subscriber<>(audience, sink);
        subscribers.put(id, subscriber);
        EventSubscription subscription = () -> remove(id);
        try {
            sink.send(CONNECTED_EVENT, id);
        } catch (IOException e) {
            remove(id);
            return subscription;
        }
        subscriber.writer = Thread.ofVirtual().name(name + "-" + id).start(() -> drain(id, subscriber));
        if (!subscribers.containsKey(id)) {
            subscriber.writer.interrupt(); // cancelled before its writer existed
        }
        return subscription;
    }

    /** To every subscription whose audience matches. Never blocks. */
    public void publish(Predicate<A> audience, String eventName, Object payload) {
        Delivery delivery = new Delivery(eventName, payload);
        subscribers.forEach((id, subscriber) -> {
            if (audience.test(subscriber.audience)) {
                enqueue(id, subscriber, delivery);
            }
        });
    }

    /** A heartbeat to every subscription; the owning service calls this on a fixed rate. */
    public void sendHeartbeat() {
        subscribers.forEach((id, subscriber) -> enqueue(id, subscriber, HEARTBEAT));
    }

    /** The audience of every open subscription, one entry per subscription. */
    public List<A> findAudiences() {
        return subscribers.values().stream().map(subscriber -> subscriber.audience).toList();
    }

    /** Id of the most recent subscription, so a publisher can notice a new client. */
    public long findLastSubscriptionId() {
        return nextId.get();
    }

    private void enqueue(long id, Subscriber<A> subscriber, Delivery delivery) {
        if (!subscriber.queue.offer(delivery)) {
            log.debug("Stream {}-{} is {} events behind — dropping one", name, id, QUEUE_CAPACITY);
        }
    }

    private void drain(long id, Subscriber<A> subscriber) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Delivery delivery = subscriber.queue.take();
                if (delivery == HEARTBEAT) {
                    subscriber.sink.sendHeartbeat();
                } else {
                    subscriber.sink.send(delivery.eventName(), delivery.payload());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalStateException e) {
            remove(id);
        }
    }

    private void remove(long id) {
        Subscriber<A> subscriber = subscribers.remove(id);
        if (subscriber != null && subscriber.writer != null) {
            subscriber.writer.interrupt();
        }
    }
}
