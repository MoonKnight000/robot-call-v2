package uz.murodjon.robotcallv2.callrecord.domain.entity;

import java.time.Instant;

/**
 * One call a reanalysis goes through.
 *
 * @param startedAt when the call took place — relative dates in its transcript ("ertaga") are
 *                  read from here, not from the day it is analysed again
 */
public record CallReanalysisTarget(long callId, Instant startedAt) {
}
