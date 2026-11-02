package uz.karvon.common.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.common.infrastructure.LocalFileStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev and test only: serves files of {@link LocalFileStorage}. Keys contain random ids, so links are not guessable.
 */
@Hidden
@Profile({"dev", "test"})
@RestController
public class LocalFileController {

    private static final String PREFIX = "/api/v1/files/";

    private final LocalFileStorage storage;

    public LocalFileController(LocalFileStorage storage) {
        this.storage = storage;
    }

    @GetMapping(PREFIX + "**")
    public ResponseEntity<byte[]> file(HttpServletRequest request) throws IOException {
        String key = request.getRequestURI().substring(request.getContextPath().length() + PREFIX.length());
        Path file = storage.resolve(key).filter(Files::isRegularFile)
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "error.http.404"));
        MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(Files.readAllBytes(file));
    }
}
