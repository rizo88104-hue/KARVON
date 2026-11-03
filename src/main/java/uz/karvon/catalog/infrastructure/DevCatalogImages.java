package uz.karvon.catalog.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uz.karvon.catalog.application.CategoryCovers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Dev and test only: serves the photos of the fake catalog from {@code dev-catalog/images} (outside the jar and the
 * Docker image) at {@value #URL_PREFIX}. The fake products link to {@code products/*.webp}; a category has a cover
 * when {@code categories/{slug}.webp} exists. The photos are free-license (docs/image-credits.md).
 */
@Component
@Profile({"dev", "test"})
public class DevCatalogImages implements WebMvcConfigurer, CategoryCovers {

    public static final String URL_PREFIX = "/api/v1/dev/images/";

    private final Path directory;

    public DevCatalogImages(@Value("${karvon.dev-catalog.images-dir:dev-catalog/images}") Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(URL_PREFIX + "**")
                .addResourceLocations(directory.toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }

    @Override
    public Optional<String> coverUrl(String slug) {
        String file = "categories/" + slug + ".webp";
        return Files.isRegularFile(directory.resolve(file)) ? Optional.of(URL_PREFIX + file) : Optional.empty();
    }
}
