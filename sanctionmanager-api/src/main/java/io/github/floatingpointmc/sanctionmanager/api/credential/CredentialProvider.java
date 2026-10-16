package io.github.floatingpointmc.sanctionmanager.api.credential;

import java.util.function.Consumer;

/**
 * Provides credentials for database authentication.
 * <p>
 * Implementations supply database credentials through a callback pattern, allowing them to manage
 * credential lifecycle and secure storage independently.
 * </p>
 */
public interface CredentialProvider {
    /**
     * Provides credentials for the given operation.
     * <p>
     * The supplied {@code Credential} is valid only for the duration of the consumer's execution.
     * Implementations should close or clean up the credential after the consumer completes.
     * </p>
     *
     * @param consumer a callback that accepts a Credential. Cannot be null.
     * @throws NullPointerException if {@code consumer} is null
     */
    void useCredential(Consumer<Credential> consumer);
}
