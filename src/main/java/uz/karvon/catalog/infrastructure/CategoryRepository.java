package uz.karvon.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.karvon.catalog.domain.Category;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByOrderBySortOrderAsc();
}
