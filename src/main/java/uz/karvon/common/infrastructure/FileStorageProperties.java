package uz.karvon.common.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code karvon.files.*}: the local directory (dev, test) or the S3-compatible bucket (prod).
 *
 * @param publicBaseUrl base URL of this API, for links to local files
 */
@ConfigurationProperties("karvon.files")
public record FileStorageProperties(@DefaultValue("./data/files") String localDir,
                                    @DefaultValue("http://localhost:8080") String publicBaseUrl,
                                    S3 s3) {

    /**
     * @param endpoint e.g. https://storage.yandexcloud.net or a MinIO URL; empty for AWS itself
     * @param linkTtl  how long a presigned photo link stays valid
     */
    public record S3(@DefaultValue("") String endpoint, @DefaultValue("us-east-1") String region,
                     @DefaultValue("") String bucket, @DefaultValue("") String accessKey,
                     @DefaultValue("") String secretKey, @DefaultValue("1h") Duration linkTtl) {
    }
}
