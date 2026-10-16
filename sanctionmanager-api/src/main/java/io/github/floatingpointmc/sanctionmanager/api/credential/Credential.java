package io.github.floatingpointmc.sanctionmanager.api.credential;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Encapsulates database authentication credentials.
 * <p>
 * This class stores a username and password securely, with automatic password zeroing after use
 * and on close. Credentials are thread-safe and support access via a callback pattern to minimize
 * the lifespan of password arrays in memory.
 * </p>
 * <p>
 * <strong>Password Handling:</strong> Passwords are cloned upon construction and stored as char arrays
 * for better security. The {@link #usePassword(PasswordConsumer)} method provides temporary access
 * to a password copy, which is automatically zeroed after the consumer completes. When the credential
 * is closed via {@link #close()}, the password array is permanently zeroed.
 * </p>
 * <p>
 * This class implements {@code AutoCloseable} and should be used in try-with-resources statements
 * to ensure prompt cleanup.
 * </p>
 */
public final class Credential implements AutoCloseable {
    @Getter
    private final String username;
    private char[] password;
    private boolean closed;

    /**
     * Constructs a Credential with the given username and password.
     * <p>
     * The password array is cloned internally; the caller's array is not modified or retained.
     * </p>
     *
     * @param username the username. Cannot be null.
     * @param password the password as a char array. Cannot be null. Will be cloned for storage.
     * @throws NullPointerException if {@code username} or {@code password} is null
     */
    public Credential(@NotNull String username, char @NotNull [] password) {
        this.username = username;
        this.password = Objects.requireNonNull(password, "password").clone();
    }

    /**
     * Provides temporary access to the password for a specific operation.
     * <p>
     * The password array is copied and passed to the consumer. The copy is automatically
     * zeroed (all characters set to {@code '\0'}) after the consumer completes, whether normally
     * or via exception.
     * </p>
     * <p>
     * <strong>Thread Safety:</strong> This method is synchronized. Only one thread may access
     * the password at a time.
     * </p>
     *
     * @param consumer a callback that receives a temporary copy of the password
     * @throws NullPointerException if {@code consumer} is null
     * @throws IllegalStateException if this credential has been closed
     */
    public synchronized void usePassword(PasswordConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");

        if (closed) {
            throw new IllegalStateException("Credential is closed");
        }

        char[] temporary = password.clone();
        try {
            consumer.accept(temporary);
        } finally {
            Arrays.fill(temporary, '\0');
        }
    }

    /**
     * Closes this credential and permanently zeros the password array.
     * <p>
     * After closing, calls to {@link #usePassword(PasswordConsumer)} will throw {@code IllegalStateException}.
     * This method is idempotent; calling it multiple times is safe.
     * </p>
     * <p>
     * <strong>Thread Safety:</strong> This method is synchronized.
     * </p>
     */
    @Override
    public synchronized void close() {
        if (!closed) {
            Arrays.fill(password, '\0');
            password = new char[0];
            closed = true;
        }
    }

    /**
     * Functional interface for securely accessing a password.
     * <p>
     * Implementations should use the password immediately and not retain references to it.
     * The provided array will be zeroed after the method completes.
     * </p>
     */
    @FunctionalInterface
    public interface PasswordConsumer {
        /**
         * Accepts and uses a temporary copy of the password.
         * <p>
         * The array will be zeroed after this method returns.
         * </p>
         *
         * @param password a temporary copy of the password. Cannot be null.
         */
        void accept(char @NotNull [] password);
    }
}