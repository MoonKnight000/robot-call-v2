package uz.murodjon.uysotvoice.config;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import uz.murodjon.uysotvoice.agent.rtp.RtpProperties;

/**
 * Shared Netty event loop for all RTP UDP endpoints. RTP packets are received
 * here and immediately handed off; no blocking work runs on these threads
 * (PROJECT.md §7.3).
 *
 * <p>Sized from the available processors rather than pinned to one thread: each loop
 * carries both the inbound packets and the 20ms playback pacer of every call assigned
 * to it, and at the ~20 concurrent calls the MVP targets a single thread turns that
 * into audible jitter. A channel stays on one loop for its lifetime, so per-call
 * ordering is unaffected.
 *
 * <p>Note: this bean is also a {@code ScheduledExecutorService}, which disables
 * Boot's task-scheduling auto-configuration — see {@code ExecutorConfig#taskScheduler()}.
 */
@Configuration
public class NettyConfig {

    private static final Logger log = LoggerFactory.getLogger(NettyConfig.class);

    @Bean(destroyMethod = "shutdownGracefully")
    public EventLoopGroup rtpEventLoopGroup(RtpProperties props) {
        int threads = props.effectiveEventLoopThreads();
        log.info("RTP event loop group: {} thread(s)", threads);
        return new NioEventLoopGroup(threads);
    }
}
