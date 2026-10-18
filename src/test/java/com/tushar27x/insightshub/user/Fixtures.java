package com.tushar27x.insightshub.user;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The contract's example payloads, {@code docs/api/fixtures/} (owned by the integration lane; read-only here). */
final class Fixtures {

    private Fixtures() {
    }

    static String read(String name) {
        try {
            return Files.readString(Path.of("docs/api/fixtures", name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
