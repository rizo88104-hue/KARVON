package uz.karvon.common.infrastructure;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import uz.karvon.common.application.FileStorage;

import java.net.URI;
import java.time.Duration;

/**
 * S3-compatible storage (AWS, Yandex Object Storage, MinIO ...). The bucket stays private: links are presigned
 * and valid for {@code karvon.files.s3.link-ttl}. Path-style addressing, which every S3-compatible service supports.
 */
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration linkTtl;

    public S3FileStorage(FileStorageProperties.S3 s3) {
        if (s3.bucket().isBlank() || s3.accessKey().isBlank() || s3.secretKey().isBlank()) {
            throw new IllegalStateException("S3_BUCKET, S3_ACCESS_KEY and S3_SECRET_KEY must be set");
        }
        this.linkTtl = s3.linkTtl();
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        var clientBuilder = S3Client.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(s3.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle);
        if (!s3.endpoint().isBlank()) {
            clientBuilder.endpointOverride(URI.create(s3.endpoint()));
            presignerBuilder.endpointOverride(URI.create(s3.endpoint()));
        }
        this.client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
        this.bucket = s3.bucket();
    }

    @Override
    public void store(String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public String url(String key) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(linkTtl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build()).url().toString();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
