package uz.murodjon.robotcallv2.report.application.dto;

/** A rendered file on its way to the browser: what to call it, how to type it, and its bytes. */
public record ReportDownload(String filename, String contentType, byte[] body) {
}
