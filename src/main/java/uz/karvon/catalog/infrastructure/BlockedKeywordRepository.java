package uz.karvon.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uz.karvon.catalog.domain.BlockedKeyword;
import uz.karvon.translation.domain.Lang;

import java.util.UUID;

public interface BlockedKeywordRepository extends JpaRepository<BlockedKeyword, UUID>, JpaSpecificationExecutor<BlockedKeyword> {

    boolean existsByKeywordAndLang(String keyword, Lang lang);

    boolean existsByKeywordAndLangAndIdNot(String keyword, Lang lang, UUID id);
}
