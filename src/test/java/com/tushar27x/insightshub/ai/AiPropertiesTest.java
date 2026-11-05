package com.tushar27x.insightshub.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code insightshub.ai.*} binds from application.yml the way Boot binds it (B44: a second constructor hid it). */
class AiPropertiesTest {

    private static Binder applicationYml() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        return Binder.get(environment);
    }

    @Test
    void theRegenerationRulesBindFromApplicationYml() throws IOException {
        AiProperties properties = applicationYml().bind("insightshub.ai", AiProperties.class).get();

        assertThat(properties.taskTimeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.regenerate()).isEqualTo(new AiProperties.Regenerate(5, 0.10, Duration.ofDays(7), 2));
    }

    @Test
    void withoutARegenerateBlockTheDefaultsApply() {
        AiProperties properties = Binder.get(new StandardEnvironment())
                .bindOrCreate("insightshub.ai", AiProperties.class);

        assertThat(properties.regenerate()).isEqualTo(AiProperties.Regenerate.DEFAULTS);
    }

    @Test
    void nonsenseThresholdsAreRejected() {
        assertThatThrownBy(() -> new AiProperties.Regenerate(0, 0.10, Duration.ofDays(7), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiProperties.Regenerate(5, 0.10, Duration.ofHours(12), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
