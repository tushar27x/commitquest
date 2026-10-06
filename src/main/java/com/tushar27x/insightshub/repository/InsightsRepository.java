package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.entity.Insights;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Id type is Long: the shared primary key copied from Users.githubId
public interface InsightsRepository extends JpaRepository<Insights, Long> {
    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Insights> findById(Long id);
}
