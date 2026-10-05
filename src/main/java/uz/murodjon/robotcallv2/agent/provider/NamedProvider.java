package uz.murodjon.robotcallv2.agent.provider;

/** A speech vendor integration chosen per agent by its stable id ({@link NamedProviderSelector}). */
public interface NamedProvider {

    /** Stable id an agent selects this provider by (e.g. {@code gemini}, {@code yandex}). */
    String name();
}
