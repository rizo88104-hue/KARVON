package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import uz.karvon.user.domain.Language;

import java.util.List;
import java.util.Locale;

/**
 * Renders notification texts from messages_uz.properties / messages_ru.properties.
 */
@Component
@RequiredArgsConstructor
public class NotificationTemplates {

    private static final Locale UZ = Locale.of("uz");
    private static final Locale RU = Locale.of("ru");

    private final MessageSource messages;

    public String render(String templateKey, List<String> params, Language language) {
        return messages.getMessage(templateKey, params.toArray(), locale(language)).trim();
    }

    static Locale locale(Language language) {
        return language == Language.RU ? RU : UZ;
    }
}
