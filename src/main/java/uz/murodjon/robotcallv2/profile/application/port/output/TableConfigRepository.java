package uz.murodjon.robotcallv2.profile.application.port.output;

public interface TableConfigRepository {

    String find(long userId, String configKey);

    String save(long userId, String configKey, String configValueJson);
}
