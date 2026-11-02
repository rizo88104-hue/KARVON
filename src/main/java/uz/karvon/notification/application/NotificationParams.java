package uz.karvon.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Template values are stored as a JSON array of strings.
 */
@Component
@RequiredArgsConstructor
public class NotificationParams {

    private final JsonMapper json;

    public String write(List<String> params) {
        return json.writeValueAsString(params);
    }

    public List<String> read(String params) {
        return List.of(json.readValue(params, String[].class));
    }
}
