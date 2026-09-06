package uz.murodjon.robotcallv2.live.application.dto;

/** Hand a live call to a human; {@code extension} is optional and may be null. */
public record TakeoverRequest(String extension) {
}
