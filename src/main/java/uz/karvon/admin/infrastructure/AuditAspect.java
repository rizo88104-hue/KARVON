package uz.karvon.admin.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.admin.application.AuditService;
import uz.karvon.common.api.Audited;
import uz.karvon.common.application.AuditSnapshotSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes a successful {@link Audited} call to the audit log. A failed call is not logged (nothing changed).
 * A problem while writing the log is logged as an error and does not fail the request: the change is done already.
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    /** Longer JSON is cut, so a huge answer does not bloat the log. */
    static final int MAX_JSON_LENGTH = 100_000;
    private static final List<String> ROLES_BY_RANK = List.of("ADMIN", "OPERATOR", "BUYER", "CUSTOMER");

    private final AuditService audit;
    private final JsonMapper json;
    private final Map<String, AuditSnapshotSource> snapshots;

    public AuditAspect(AuditService audit, JsonMapper json, List<AuditSnapshotSource> snapshots) {
        this.audit = audit;
        this.json = json;
        this.snapshots = snapshots.stream()
                .collect(Collectors.toMap(AuditSnapshotSource::entityType, Function.identity()));
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint call, Audited audited) throws Throwable {
        String entityId = idArgument(call, audited.idParam());
        String before = snapshot(audited.entityType(), entityId);
        Object result = call.proceed();
        try {
            Object body = result instanceof ResponseEntity<?> entity ? entity.getBody() : result;
            if (entityId == null) {
                entityId = idOf(body);
            }
            String after = body == null || body instanceof byte[] ? snapshot(audited.entityType(), entityId) : write(body);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            audit.record(new AuditService.Entry(UUID.fromString(authentication.getName()), topRole(authentication),
                    audited.action(), audited.entityType(), entityId, before, after, clientIp()));
        } catch (RuntimeException ex) {
            log.error("Could not write the audit log for {}", audited.action(), ex);
        }
        return result;
    }

    private String snapshot(String entityType, String entityId) {
        AuditSnapshotSource source = snapshots.get(entityType);
        if (source == null) {
            return null;
        }
        try {
            return source.snapshot(entityId).map(this::write).orElse(null);
        } catch (RuntimeException ex) {
            // e.g. NOT_FOUND for an id that does not exist: the call itself will answer that.
            return null;
        }
    }

    private static String idArgument(ProceedingJoinPoint call, String idParam) {
        String[] names = ((MethodSignature) call.getSignature()).getParameterNames();
        Object[] args = call.getArgs();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(idParam) && args[i] != null) {
                return args[i].toString();
            }
        }
        return null;
    }

    private String idOf(Object body) {
        if (body == null || body instanceof byte[]) {
            return null;
        }
        JsonNode tree = json.valueToTree(body);
        JsonNode id = tree.isObject() ? tree.get("id") : null;
        return id == null || id.isNull() ? null : id.asString();
    }

    private String write(Object value) {
        String text = json.writeValueAsString(value);
        return text.length() > MAX_JSON_LENGTH ? text.substring(0, MAX_JSON_LENGTH) : text;
    }

    static String topRole(Authentication authentication) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .collect(Collectors.toSet());
        return ROLES_BY_RANK.stream().filter(roles::contains).findFirst().orElse("UNKNOWN");
    }

    /** The first X-Forwarded-For address (set by our reverse proxy), else the connection address. */
    private static String clientIp() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(attributes -> ((ServletRequestAttributes) attributes).getRequest())
                .map(AuditAspect::ip)
                .orElse(null);
    }

    private static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip == null ? null : ip.substring(0, Math.min(ip.length(), 64));
    }
}
