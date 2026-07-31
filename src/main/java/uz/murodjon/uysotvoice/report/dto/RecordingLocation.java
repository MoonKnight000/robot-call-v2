package uz.murodjon.uysotvoice.report.dto;

/** Where a call recording lives — object storage (redirect) or local disk (serve directly). */
public sealed interface RecordingLocation permits RecordingRedirect, RecordingFile {
}
