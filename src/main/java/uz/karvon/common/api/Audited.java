package uz.karvon.common.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put on every write endpoint under /api/v1/admin/**: a successful call is written to the audit log
 * (who, role, action, object, before/after JSON, IP). The id of the object is the method parameter named
 * {@link #idParam()}; when there is none (e.g. a create) it is read from the "id" of the result.
 * "before" is filled when an {@link uz.karvon.common.application.AuditSnapshotSource} exists for {@link #entityType()}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** What was done, e.g. "BANNER_CREATE". */
    String action();

    /** What kind of object, e.g. "BANNER". */
    String entityType();

    /** Name of the method parameter that holds the object id. */
    String idParam() default "id";
}
