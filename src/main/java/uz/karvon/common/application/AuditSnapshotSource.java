package uz.karvon.common.application;

import java.util.Optional;
import java.util.function.Function;

/**
 * Gives the audit log the state of an object before a change. One bean per entity type; a module registers it
 * for the objects its admin endpoints change. {@code id} is null for single objects such as settings.
 */
public interface AuditSnapshotSource {

    /** The entity type it serves, as in {@code @Audited(entityType = ...)}. */
    String entityType();

    /** An API view (never an entity) of the object, or empty when it does not exist. */
    Optional<Object> snapshot(String id);

    static AuditSnapshotSource of(String entityType, Function<String, Optional<?>> finder) {
        return new AuditSnapshotSource() {
            @Override
            public String entityType() {
                return entityType;
            }

            @Override
            public Optional<Object> snapshot(String id) {
                return finder.apply(id).map(Object.class::cast);
            }
        };
    }
}
