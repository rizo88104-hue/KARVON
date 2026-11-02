package uz.karvon.admin.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.karvon.admin.domain.Banner;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BannerRepository extends JpaRepository<Banner, UUID> {

    List<Banner> findAllByOrderBySortOrderAscCreatedAtAsc();

    @Query("""
            select b from Banner b
            where b.active = true and (b.activeFrom is null or b.activeFrom <= :now) and (b.activeTo is null or b.activeTo > :now)
            order by b.sortOrder, b.createdAt""")
    List<Banner> findVisible(@Param("now") Instant now);
}
