package uz.murodjon.uysotvoice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One virtual thread per task. Blocking call-handling code runs here, keeping
 * the ARI WebSocket / Netty event-loop threads free (PROJECT.md §7.3).
 */
@Configuration
public class ExecutorConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService callExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Explicit scheduler for {@code @Scheduled} tasks. Required: the
     * {@code rtpEventLoopGroup} bean is a {@link java.util.concurrent.ScheduledExecutorService},
     * so Boot's task-scheduling auto-configuration backs off and Spring would
     * otherwise run scheduled tasks on the single RTP event-loop thread (which
     * also rejects the slightly-negative initial delays Spring computes).
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); // dialer dispatch + stale sweeper + alerting
        scheduler.setThreadNamePrefix("sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
