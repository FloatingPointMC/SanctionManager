package io.github.floatingpointmc.sanctionmanager.api.credential;

import java.util.function.Consumer;

public interface CredentialProvider {
    void useCredential(Consumer<Credential> consumer);
}
