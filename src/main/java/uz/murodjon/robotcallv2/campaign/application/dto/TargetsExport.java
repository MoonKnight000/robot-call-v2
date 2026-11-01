package uz.murodjon.robotcallv2.campaign.application.dto;

/** A campaign's targets rendered as a CSV file: what to call it and its UTF-8 bytes. */
public record TargetsExport(String filename, byte[] body) {
}
