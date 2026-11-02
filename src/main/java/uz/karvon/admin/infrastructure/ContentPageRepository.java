package uz.karvon.admin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.admin.domain.ContentPage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentPageRepository extends JpaRepository<ContentPage, UUID> {

    Optional<ContentPage> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    List<ContentPage> findAllByOrderBySlugAsc();
}
