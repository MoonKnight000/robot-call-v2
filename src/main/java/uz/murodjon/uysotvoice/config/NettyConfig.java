package uz.murodjon.uysotvoice.config;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared Netty event loop for all RTP UDP endpoints. RTP packets are received
 * here and immediately handed off; no blocking work runs on these threads
 * (PROJECT.md §7.3).
 *
 * <p>Note: this bean is also a {@code ScheduledExecutorService}, which disables
 * Boot's task-scheduling auto-configuration — see {@code ExecutorConfig#taskScheduler()}.
 */
@Configuration
public class NettyConfig {

    @Bean(destroyMethod = "shutdownGracefully")
    public EventLoopGroup rtpEventLoopGroup() {
        return new NioEventLoopGroup(1);
    }
}
