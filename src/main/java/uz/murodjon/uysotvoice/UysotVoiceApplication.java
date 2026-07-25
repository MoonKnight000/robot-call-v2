package uz.murodjon.uysotvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // dialer dispatch loop + stale-call sweeper (Stage 10)
public class UysotVoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UysotVoiceApplication.class, args);
    }
}
