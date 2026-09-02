package uz.murodjon.robotcallv2.dialer.infrastructure.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;

/**
 * RabbitMQ wiring for the call-task queue (PROJECT.md §5.2, §10). A durable queue
 * carries {@link CallTask} messages from the dispatcher to the consumer; the JSON
 * converter makes both the producer and the listener serialize records as JSON.
 */
@Configuration
@EnableRabbit
public class RabbitConfig {

    public static final String CALL_TASK_QUEUE = "call.tasks";

    @Bean
    public Queue callTaskQueue() {
        return new Queue(CALL_TASK_QUEUE, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
