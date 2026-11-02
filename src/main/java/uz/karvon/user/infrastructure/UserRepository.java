package uz.karvon.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.karvon.user.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);

    /** Unlinks this Telegram chat from every other user (a chat belongs to one user). */
    @Modifying
    @Query("update User u set u.telegramChatId = null where u.telegramChatId = :chatId and u.id <> :userId")
    int unlinkTelegramChatFromOthers(@Param("chatId") long chatId, @Param("userId") UUID userId);
}
