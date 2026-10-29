package uz.karvon.common.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/ping")
@RequiredArgsConstructor
public class PingController {

    private final Clock clock;

    @GetMapping
    public PingResponse ping() {
        return new PingResponse("ok", Instant.now(clock));
    }
}
