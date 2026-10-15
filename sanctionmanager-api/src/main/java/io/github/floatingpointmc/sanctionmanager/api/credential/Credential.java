package io.github.floatingpointmc.sanctionmanager.api.credential;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public final class Credential implements AutoCloseable {
    @Getter
    private final String username;
    private char[] password;
    private boolean closed;

    public Credential(@NotNull String username, char @NotNull [] password) {
        this.username = username;
        this.password = Objects.requireNonNull(password, "password").clone();
    }

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

    @Override
    public synchronized void close() {
        if (!closed) {
            Arrays.fill(password, '\0');
            password = new char[0];
            closed = true;
        }
    }

    @FunctionalInterface
    public interface PasswordConsumer {
        void accept(char @NotNull [] password);
    }
}