package uz.karvon.common.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Presigning is computed locally, so no storage server is needed. */
class S3FileStorageTest {

    @Test
    void photoLinksUseThePublicEndpointWhenTheApiTalksToAPrivateOne() {
        S3FileStorage storage = new S3FileStorage(new FileStorageProperties.S3("http://minio:9000", "us-east-1",
                "photos", "access", "secret", Duration.ofHours(1), "https://files.example.test"));

        String link = storage.url("orders/1/a.jpg");

        assertThat(link).startsWith("https://files.example.test/photos/orders/1/a.jpg?")
                .contains("X-Amz-Signature=").contains("X-Amz-Expires=3600");
    }

    @Test
    void withoutAPublicEndpointLinksUseTheEndpoint() {
        S3FileStorage storage = new S3FileStorage(new FileStorageProperties.S3("https://storage.yandexcloud.net",
                "ru-central1", "photos", "access", "secret", Duration.ofMinutes(10), ""));

        assertThat(storage.url("a.jpg")).startsWith("https://storage.yandexcloud.net/photos/a.jpg?")
                .contains("X-Amz-Expires=600");
    }
}
