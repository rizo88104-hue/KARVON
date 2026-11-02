package uz.karvon.common.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.karvon.common.application.FileStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Dev and test: files under {@code karvon.files.local-dir}, served by {@link LocalFileController}.
 */
@Component
@Profile({"dev", "test"})
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final String baseUrl;

    public LocalFileStorage(FileStorageProperties properties) {
        this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
        String base = properties.publicBaseUrl();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public void store(String key, byte[] content, String contentType) {
        Path file = resolve(key).orElseThrow(() -> new IllegalArgumentException("Bad file key " + key));
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public String url(String key) {
        return baseUrl + "/api/v1/files/" + key;
    }

    @Override
    public void delete(String key) {
        resolve(key).ifPresent(file -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    /** The file for a key, only if it stays inside the root directory (no "../"). */
    public Optional<Path> resolve(String key) {
        Path file = root.resolve(key).normalize();
        return file.startsWith(root) && !file.equals(root) ? Optional.of(file) : Optional.empty();
    }
}
