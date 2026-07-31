package uz.murodjon.uysotvoice.report.dto;

import java.nio.file.Path;

public record RecordingFile(Path path, String filename) implements RecordingLocation {
}
