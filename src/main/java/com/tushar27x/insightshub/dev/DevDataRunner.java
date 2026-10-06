package com.tushar27x.insightshub.dev;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
@Profile("dev")
public class DevDataRunner {
    @Bean
    CommandLineRunner roundTrip(UsersRepository usersRepository, InsightsRepository insightsRepository) {
        return args -> {
            long id = 100276134L;
            Users user = usersRepository.findById(id).orElseGet( () -> usersRepository.save(new Users(id, "tushar27x")));
            log.info("Save user {}, createdAt={}", user.getGithubId(), user.getCreatedAt());

            Insights insights = insightsRepository.findById(id).orElseGet(()-> new Insights(user));
            insights.setArchetype(Archetype.CODE_CRUSADER);
            insights.setStats(Map.of("total_commits", 42, "top_languages", List.of("Java", "Python")));
            insightsRepository.save(insights);

            Insights loaded = insightsRepository.findById(id).orElseThrow();
            log.info("Loaded insights userId={} archetype={} stats={}",
                    loaded.getUserId(), loaded.getArchetype(), loaded.getStats()
            );
            log.info("Insights belong to {}", loaded.getUser().getLogin());
        };
    }
}
