package com.tushar27x.insightshub.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The configuration the image runs with: {@code application.yml} with {@code application-prod.yml}
 * on top, as Boot layers them, without starting anything (no DB, Redis or secrets needed). Values
 * that point at the environment ({@code ${DB_USER}} …) stay unresolved; only bind what doesn't use them.
 */
public final class ProdProfile {

    private ProdProfile() {
    }

    public static StandardEnvironment environment() {
        StandardEnvironment environment = new StandardEnvironment();
        // the profile file wins, so it goes first
        add(environment, "application-prod.yml");
        add(environment, "application.yml");
        return environment;
    }

    /** {@code application.yml} alone, as a local run or the tests see it. */
    public static StandardEnvironment environmentWithoutProfile() {
        StandardEnvironment environment = new StandardEnvironment();
        add(environment, "application.yml");
        return environment;
    }

    public static Binder binder() {
        return Binder.get(environment());
    }

    private static void add(StandardEnvironment environment, String file) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
            sources.forEach(source -> environment.getPropertySources().addLast(source));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
