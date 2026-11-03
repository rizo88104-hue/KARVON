package uz.karvon.notification.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Notifications are sent on a small bounded pool. When its queue is full the task is refused and the notification
 * stays PENDING in the table; the retry job sends it later.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({NotificationProperties.class, TelegramProperties.class, SmsProperties.class})
public class NotificationConfig {

    public static final String EXECUTOR = "notificationExecutor";

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor(NotificationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notify-");
        executor.setCorePoolSize(properties.threads());
        executor.setMaxPoolSize(properties.threads());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
