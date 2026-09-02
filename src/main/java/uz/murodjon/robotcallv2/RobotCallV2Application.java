package uz.murodjon.robotcallv2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // dialer dispatch loop + stale-call sweeper (Stage 10)
public class RobotCallV2Application {

    public static void main(String[] args) {
        SpringApplication.run(RobotCallV2Application.class, args);
    }
}
