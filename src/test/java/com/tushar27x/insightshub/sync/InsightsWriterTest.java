package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InsightsWriterTest {

    private static final long ID = 583231L;
    private static final Instant SYNCED_AT = Instant.parse("2026-10-14T12:00:00Z");
    private static final AiInsights TEXTS = new AiInsights("year", "week", "craft", Map.of("app", "An app."),
            new CharacterSheet("octocat the Monk", "Calm."));

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final InsightsRepository insightsRepository = mock(InsightsRepository.class);
    private final InsightsWriter writer = new InsightsWriter(usersRepository, insightsRepository);
    private final Users user = new Users(ID, "octocat");

    @BeforeEach
    void setUp() {
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
    }

    @Test
    void firstSyncCreatesTheRowWithEverything() {
        when(insightsRepository.findById(ID)).thenReturn(Optional.empty());

        writer.save(ID, Map.of("total_commits", 42), AiTestData.insights(), TEXTS, SYNCED_AT);

        Insights saved = saved();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getStats()).containsEntry("total_commits", 42);
        assertThat(saved.getArchetype()).isEqualTo(Archetype.MONK);
        assertThat(saved.getArchetypeScores()).containsEntry("MONK", 52).containsEntry("CLERIC", 0).hasSize(8);
        assertThat(saved.getAlgoVersion()).isEqualTo(1);
        assertThat(saved.getYearInReview()).isEqualTo("year");
        assertThat(saved.getWeeklyReview()).isEqualTo("week");
        assertThat(saved.getCraftReview()).isEqualTo("craft");
        assertThat(saved.getRepoSummaries()).containsEntry("app", "An app.");
        assertThat(saved.getCharacterTitle()).isEqualTo("octocat the Monk");
        assertThat(saved.getCharacterLore()).isEqualTo("Calm.");
        assertThat(user.getLastSyncedAt()).isEqualTo(SYNCED_AT);
    }

    @Test
    void laterSyncsUpdateTheExistingRow() {
        Insights existing = new Insights(user);
        existing.setWeeklyReview("old");
        when(insightsRepository.findById(ID)).thenReturn(Optional.of(existing));

        writer.save(ID, Map.of(), AiTestData.insights(), TEXTS, SYNCED_AT);

        assertThat(saved()).isSameAs(existing);
        assertThat(existing.getWeeklyReview()).isEqualTo("week");
    }

    @Test
    void markNeedsReauthFlagsTheUser() {
        writer.markNeedsReauth(ID);

        assertThat(user.isNeedsReauth()).isTrue();
    }

    private Insights saved() {
        ArgumentCaptor<Insights> captor = ArgumentCaptor.forClass(Insights.class);
        verify(insightsRepository).save(captor.capture());
        return captor.getValue();
    }
}
