package com.tushar27x.insightshub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "insights")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Insights {

    // Copied from user.githubId by @MapsId; never set by hand
    @Id
    private Long userId;

    @OneToOne(fetch = LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private Users user;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> stats = new HashMap<>();

    @Setter
    @Enumerated(EnumType.STRING)
    private Archetype archetype;

    // null = not generated yet
    @Setter
    private String roast;

    @Setter
    private String weeklyReview;

    @Setter
    private String craftReview;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Insights(Users user) {
        this.user = user;
    }
}
