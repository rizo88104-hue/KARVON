package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import uz.karvon.common.infrastructure.I18nConfig;
import uz.karvon.user.domain.Language;

import java.util.List;
import java.util.Locale;

/**
 * Notification texts from messages_uz.properties / messages_ru.properties in the user's language.
 */
@Component
@RequiredArgsConstructor
public class TemplateRenderer {

    private final MessageSource messages;

    public String render(String templateKey, List<String> params, Language language) {
        return messages.getMessage(templateKey, params.toArray(), locale(language)).trim();
    }

    static Locale locale(Language language) {
        return language == Language.RU ? I18nConfig.RU : I18nConfig.UZ;
    }
}
