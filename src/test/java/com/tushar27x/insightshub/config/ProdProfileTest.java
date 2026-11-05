package com.tushar27x.insightshub.config;

import com.tushar27x.insightshub.hero.HeroProperties;
import com.tushar27x.insightshub.notify.DigestProperties;
import com.tushar27x.insightshub.sync.SyncProperties;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B10: what {@code application-prod.yml} sets, bound the way Boot binds it (so a value Hikari or a
 * record can't read fails here, not on the instance).
 */
class ProdProfileTest {

    private final Binder binder = ProdProfile.binder();

    @Test
    void hikariPoolIsSmallAndLetsNeonSuspend() {
        HikariConfig hikari = binder.bind("spring.datasource.hikari", Bindable.ofInstance(new HikariConfig())).get();

        assertThat(hikari.getMaximumPoolSize()).isEqualTo(4);
        assertThat(hikari.getMinimumIdle()).isZero();
        assertThat(hikari.getIdleTimeout()).isEqualTo(Duration.ofSeconds(60).toMillis());
        assertThat(hikari.getKeepaliveTime()).isZero();
        assertThat(hikari.getMaxLifetime()).isEqualTo(Duration.ofMinutes(30).toMillis());
    }

    @Test
    void jobsRunTwoUsersAtATime() {
        assertThat(binder.bind("insightshub.sync", SyncProperties.class).get().concurrency()).isEqualTo(2);
        assertThat(binder.bind("insightshub.hero", HeroProperties.class).get().concurrency()).isEqualTo(2);
        assertThat(binder.bind("insightshub.digest", DigestProperties.class).get().concurrency()).isEqualTo(2);
    }

    @Test
    void shutdownFitsComposesGracePeriod() {
        Duration requests = binder.bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).get();
        Duration jobs = binder.bind("spring.task.scheduling.shutdown.await-termination-period", Duration.class).get();

        assertThat(binder.bind("server.shutdown", String.class).get()).isEqualTo("graceful");
        assertThat(binder.bind("spring.task.scheduling.shutdown.await-termination", Boolean.class).get()).isTrue();
        assertThat(requests.plus(jobs)).isLessThan(Duration.ofSeconds(40)); // compose: 45 s
    }

    @Test
    void defaultProfileKeepsItsOwnValues() {
        Binder defaults = Binder.get(ProdProfile.environmentWithoutProfile());

        assertThat(defaults.bind("insightshub.sync", SyncProperties.class).get().concurrency()).isEqualTo(4);
        assertThat(defaults.bind("server.forward-headers-strategy", String.class).isBound()).isFalse();
    }
}
