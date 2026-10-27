package uz.murodjon.robotcallv2.scenario.application.dto;

/**
 * Visual canvas camera / viewport settings (pan & zoom).
 *
 * @param x pan offset X
 * @param y pan offset Y
 * @param zoom zoom ratio (1.0 = 100%)
 */
public record CanvasViewportDto(double x, double y, double zoom) {

    public static CanvasViewportDto defaultViewport() {
        return new CanvasViewportDto(0.0, 0.0, 1.0);
    }
}
