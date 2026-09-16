package uz.murodjon.robotcallv2.callqueue.domain.service;

import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Shape rules for a queue: timers within sane bounds, overflow pointing at something, members unique. */
public final class CallQueueValidator {

    private static final Pattern SKILL = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Pattern E164 = Pattern.compile(PhoneNumbers.E164_REGEX);

    private CallQueueValidator() {
    }

    public static void validate(CallQueue queue) {
        if (queue.name() == null || queue.name().isBlank()) {
            fail("name is required");
        }
        range("ringTimeoutSec", queue.ringTimeoutSec(), 5, 120);
        range("wrapUpSec", queue.wrapUpSec(), 0, 600);
        range("maxWaitSec", queue.maxWaitSec(), 10, 7200);
        range("maxLength", queue.maxLength(), 0, 1000);
        timer("announcePositionEverySec", queue.announcePositionEverySec(), 20, 900);
        timer("periodicAnnounceEverySec", queue.periodicAnnounceEverySec(), 30, 1800);
        if (queue.periodicAnnounceEverySec() > 0
                && (queue.periodicAnnounceText() == null || queue.periodicAnnounceText().isBlank())) {
            fail("periodicAnnounceText is required when periodicAnnounceEverySec is set");
        }
        timer("callbackOfferAfterSec", queue.callbackOfferAfterSec(), 10, 3600);
        range("serviceLevelSec", queue.serviceLevelSec(), 5, 600);
        range("stickySeconds", queue.stickySeconds(), 0, 120);
        switch (queue.overflowAction()) {
            case QUEUE, AI_AGENT, VOICEMAIL -> {
                if (queue.overflowTargetId() == null) {
                    fail("overflowTarget must be the id of the " + queue.overflowAction() + " to overflow to");
                }
            }
            case EXTERNAL_NUMBER -> {
                if (queue.overflowTarget() == null || !E164.matcher(queue.overflowTarget().trim()).matches()) {
                    fail("overflowTarget must be a phone number for EXTERNAL_NUMBER");
                }
            }
            case CALLBACK, HANGUP -> {
                // no target
            }
        }
        Set<Long> seen = new HashSet<>();
        for (CallQueueMember member : queue.members()) {
            if (!seen.add(member.userId())) {
                fail("user " + member.userId() + " is listed twice");
            }
            range("priority", member.priority(), 0, 100);
            for (String skill : member.skills()) {
                if (skill == null || !SKILL.matcher(skill).matches()) {
                    fail("skill '" + skill + "' must be 1-32 letters, digits, '-' or '_'");
                }
            }
        }
    }

    private static void range(String field, int value, int min, int max) {
        if (value < min || value > max) {
            fail(field + " must be between " + min + " and " + max);
        }
    }

    /** A timer is off at 0, otherwise within bounds. */
    private static void timer(String field, int value, int min, int max) {
        if (value != 0) {
            range(field, value, min, max);
        }
    }

    private static void fail(String problem) {
        throw new ValidationException(ErrorCode.CALL_QUEUE_INVALID, problem);
    }
}
