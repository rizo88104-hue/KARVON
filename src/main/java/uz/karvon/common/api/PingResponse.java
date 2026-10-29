package uz.karvon.common.api;

import java.time.Instant;

public record PingResponse(String status, Instant time) {
}
