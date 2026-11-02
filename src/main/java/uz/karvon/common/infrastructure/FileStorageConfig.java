package uz.karvon.common.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import uz.karvon.common.application.FileStorage;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

    /** Every profile except dev and test (those use {@link LocalFileStorage}). */
    @Bean
    @Profile("!dev & !test")
    public FileStorage s3FileStorage(FileStorageProperties properties) {
        return new S3FileStorage(properties.s3());
    }
}
