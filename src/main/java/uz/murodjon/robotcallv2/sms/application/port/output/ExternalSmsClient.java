package uz.murodjon.robotcallv2.sms.application.port.output;

public interface ExternalSmsClient {

    boolean send(String phone, String message);
}
