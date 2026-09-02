package uz.murodjon.robotcallv2.engine.domain.service;

public final class EngineConfigValidator {

    private EngineConfigValidator() {
    }

    public static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    public static String orDefault(String chosen, String fallback) {
        return isSet(chosen) ? chosen : fallback;
    }
}
