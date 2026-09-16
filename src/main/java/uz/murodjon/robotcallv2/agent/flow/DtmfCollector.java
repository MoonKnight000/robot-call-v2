package uz.murodjon.robotcallv2.agent.flow;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Keypad digits of one call, as a menu reads them. Digits arrive on the ARI event thread
 * and are consumed by the call's flow thread; a digit also fires the barge-in hook, so a
 * caller who knows the menu can press before the prompt has finished.
 */
public final class DtmfCollector {

    /** Granularity of the waits below — how quickly a hang-up is noticed while waiting. */
    private static final long SLICE_MS = 100;

    private final BlockingQueue<Character> digits = new LinkedBlockingQueue<>();
    private volatile Runnable bargeIn;

    public void offer(char digit) {
        digits.offer(digit);
        Runnable hook = bargeIn;
        if (hook != null) {
            hook.run();
        }
    }

    /** What to do the moment a key is pressed (stop the prompt); null for nothing. */
    public void onDigit(Runnable hook) {
        this.bargeIn = hook;
    }

    public void clear() {
        digits.clear();
    }

    public boolean hasPending() {
        return !digits.isEmpty();
    }

    /** The next digit within {@code timeoutMs}, or null; gives up early once {@code stop} says so. */
    public Character poll(long timeoutMs, BooleanSupplier stop) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        while (!stop.getAsBoolean()) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                return digits.poll();
            }
            Character digit = digits.poll(Math.min(left, SLICE_MS), TimeUnit.MILLISECONDS);
            if (digit != null) {
                return digit;
            }
        }
        return null;
    }

    /**
     * Up to {@code maxDigits} keys: the first within {@code firstTimeoutMs}, each next within
     * {@code interDigitTimeoutMs} of the one before; {@code #} ends the entry early and is not
     * part of it. Empty when nothing was pressed.
     */
    public String collect(int maxDigits, long firstTimeoutMs, long interDigitTimeoutMs, BooleanSupplier stop)
            throws InterruptedException {
        StringBuilder entry = new StringBuilder();
        Character first = poll(firstTimeoutMs, stop);
        if (first == null) {
            return "";
        }
        if (first != '#') {
            entry.append(first);
        }
        while (first != '#' && entry.length() < maxDigits) {
            Character next = poll(interDigitTimeoutMs, stop);
            if (next == null || next == '#') {
                break;
            }
            entry.append(next);
        }
        return entry.toString();
    }
}
