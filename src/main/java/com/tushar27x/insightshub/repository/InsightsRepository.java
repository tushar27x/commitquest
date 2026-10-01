package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.entity.Insights;
import org.springframework.data.jpa.repository.JpaRepository;

// Id type is Long: the shared primary key copied from Users.githubId
public interface InsightsRepository extends JpaRepository<Insights, Long> {
}
