package io.github.floatingpointmc.sanctionmanager.api.model;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.service.EntryService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.UUID;

/**
 * A read-only interface representing a sanction entry.
 * <p>
 * {@code Entry} instances are created via {@link io.github.floatingpointmc.sanctionmanager.api.factory.EntryFactory}
 * and represent either a pending entry (not yet persisted) or a persistent snapshot of a database record.
 * </p>
 * <p>
 * <strong>Pending Entries:</strong> Before persistence, calls to {@link #getId()}, {@link #getRelId()},
 * {@link #getExecutingTime()}, and methods querying withdrawn or overridden state will throw {@code IllegalStateException}.
 * </p>
 * <p>
 * <strong>Query Snapshots:</strong> After persistence, query results return independent, read-only snapshots.
 * Multiple queries do not guarantee the same Java object instance. Snapshots are not automatically refreshed
 * when the database is modified.
 * </p>
 * <p>
 * <strong>Immutability:</strong> This interface does not expose any methods to modify Entry state.
 * All returned collections and objects are defensive copies.
 * </p>
 */
public interface Entry {
    /**
     * Gets the unique ID assigned by the persistence layer.
     * <p>
     * For pending entries (before {@link EntryService#execute(Entry)}),
     * this method throws {@code IllegalStateException}.
     * </p>
     *
     * @return the persistent ID
     * @throws IllegalStateException if the entry has not been persisted
     */
    long getId() throws IllegalStateException;

    /**
     * Gets the relation ID, assigned by the persistence layer and unique per entry type.
     * <p>
     * For pending entries (before {@link EntryService#execute(Entry)}),
     * this method throws {@code IllegalStateException}.
     * </p>
     *
     * @return the relation ID
     * @throws IllegalStateException if the entry has not been persisted
     */
    long getRelId() throws IllegalStateException;

    /**
     * Gets the entry type name (e.g., "BAN", "MUTE", "KICK").
     * <p>
     * The type is immutable and must be non-empty. The exact type name depends on application
     * configuration and type registration.
     * </p>
     *
     * @return the type name, never null
     */
    @NotNull String getType();

    /**
     * Gets the UUID of the player or entity being sanctioned.
     * <p>
     * The target UUID is immutable and must not be null.
     * </p>
     *
     * @return the target UUID, never null
     */
    @NotNull UUID getTarget();

    /**
     * Gets the UUID of the player or system that issued this sanction.
     * <p>
     * The executor UUID is immutable and must not be null.
     * </p>
     *
     * @return the executor UUID, never null
     */
    @NotNull UUID getExecutor();

    /**
     * Gets the time at which the sanction expires.
     * <p>
     * If the sanction has no expiration (permanent), returns {@code null}.
     * For immediate expiration, this equals {@link #getExecutingTime()}.
     * </p>
     *
     * @return the expiry time, or {@code null} if the entry never expires
     * @throws IllegalStateException if the entry has not been persisted
     */
    @Nullable Date getExpiryTime() throws IllegalStateException;

    /**
     * Gets the reason provided for this sanction.
     * <p>
     * The reason may be {@code null} if no reason was provided.
     * </p>
     *
     * @return the reason, or {@code null}
     */
    @Nullable String getReason();

    /**
     * Gets the timestamp when this entry was persisted.
     * <p>
     * For pending entries (before {@link EntryService#execute(Entry)}),
     * this method throws {@code IllegalStateException}.
     * </p>
     *
     * @return the executing time, never null
     * @throws IllegalStateException if the entry has not been persisted
     */
    @NotNull Date getExecutingTime() throws IllegalStateException;

    /**
     * Indicates whether this entry has been withdrawn.
     * <p>
     * Equivalent to {@code getWithdrawnBy() != null}. A withdrawn entry can no longer be active
     * regardless of its expiration status.
     * </p>
     *
     * @return {@code true} if this entry has been withdrawn
     * @throws IllegalStateException if the entry has not been persisted
     */
    boolean isWithdrawn() throws IllegalStateException;

    /**
     * Gets the UUID of the player or system that withdrew this entry.
     * <p>
     * Returns {@code null} if the entry has not been withdrawn.
     * </p>
     *
     * @return the UUID of the player or system that withdrew this entry, or {@code null}
     * @throws IllegalStateException if the entry has not been persisted
     */
    @Nullable UUID getWithdrawnBy() throws IllegalStateException;

    /**
     * Gets the reason provided for withdrawing this entry.
     * <p>
     * The reason may be {@code null} if no reason was provided.
     * This is only meaningful if {@link #isWithdrawn()} returns {@code true}.
     * </p>
     *
     * @return the withdrawal reason, or {@code null}
     * @throws IllegalStateException if the entry has not been persisted
     */
    @Nullable String getWithdrawalReason() throws IllegalStateException;

    /**
     * Indicates whether this entry is permanently marked as overridden.
     * <p>
     * An overridden entry is no longer active. Even if the overriding entry is later
     * deleted or withdrawn, an overridden entry remains permanently overridden.
     * </p>
     *
     * @return {@code true} if this entry is marked as overridden
     * @throws IllegalStateException if the entry has not been persisted
     */
    boolean isOverridden() throws IllegalStateException;

    /**
     * Gets the ID of the entry that overrode this entry.
     * <p>
     * Returns {@code null} if no entry has been overridden by another, or if the overriding entry
     * was deleted. Presence or absence of this value does not determine overridden status;
     * use {@link #isOverridden()} for the authoritative check.
     * </p>
     *
     * @return the ID of the overriding entry, or {@code null}
     * @throws IllegalStateException if the entry has not been persisted
     */
    @Nullable Long getOverriddenBy() throws IllegalStateException;

    /**
     * Gets a defensive copy of the extended data associated with this entry.
     * <p>
     * The returned {@code JsonObject} is a deep copy. Modifications to the returned object
     * do not affect the entry. If extended data support is disabled for this entry type,
     * returns an empty JSON object {@code {}}.
     * </p>
     *
     * @return a deep copy of the extended data, never null
     */
    @NotNull JsonObject getData();
}