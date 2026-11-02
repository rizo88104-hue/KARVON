package uz.karvon.notification.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uz.karvon.notification.application.NotificationEventListener;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Turns on @Async and defines the bounded executor notifications are sent on.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({NotificationProperties.class, TelegramProperties.class})
public class NotificationConfig {

    @Bean(NotificationEventListener.EXECUTOR)
    public TaskExecutor notificationExecutor(NotificationProperties properties) {
        if (!properties.async()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notify-");
        executor.setCorePoolSize(properties.threads());
        executor.setMaxPoolSize(Math.max(properties.threads(), properties.maxThreads()));
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
