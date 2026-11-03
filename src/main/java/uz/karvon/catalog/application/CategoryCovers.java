package uz.karvon.catalog.application;

import java.util.Optional;

/**
 * Cover photos of categories. Only the dev catalog has them for now ({@code DevCatalogImages}); without a bean
 * (prod) categories have no cover.
 */
public interface CategoryCovers {

    /** A URL of the category's cover photo, if there is one. */
    Optional<String> coverUrl(String slug);
}
