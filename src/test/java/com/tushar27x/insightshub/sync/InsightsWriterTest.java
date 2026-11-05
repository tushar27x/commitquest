package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiField;
import com.tushar27x.insightshub.ai.AiInputs;
import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.Regeneration;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final InsightsWriter writer = new InsightsWriter(usersRepository, insightsRepository,
            JsonMapper.builder().build());
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
        // B11: when and from what the texts were written
        assertThat(saved.getAiGeneratedAt()).isEqualTo(SYNCED_AT);
        assertThat(saved.isAiFallback()).isFalse();
        assertThat(saved.getAiInputs())
                .containsEntry("archetype", "MONK")
                .containsEntry("total_commits", 42)
                .containsEntry("total_prs", 7)
                .containsEntry("total_stars", 12)
                .containsEntry("top_repos", List.of("app"))
                .containsEntry("top_languages", List.of("Java", "Go"))
                .containsKey("scores");
    }

    @Test
    void textsWithAFallbackAreMarked() {
        when(insightsRepository.findById(ID)).thenReturn(Optional.empty());
        AiInsights partial = new AiInsights("year", "week", "craft", Map.of(), new CharacterSheet("t", "l"),
                Set.of(AiField.CRAFT_REVIEW));

        writer.save(ID, Map.of(), AiTestData.insights(), partial, SYNCED_AT);

        assertThat(saved().isAiFallback()).isTrue();
    }

    @Test
    void keepingTheTextsRewritesOnlyStatsAndScores() {
        Insights existing = new Insights(user);
        existing.setYearInReview("old year");
        existing.setCharacterTitle("old title");
        Instant writtenAt = SYNCED_AT.minusSeconds(86_400);
        existing.setAiGeneratedAt(writtenAt);
        existing.setAiInputs(Map.of("archetype", "ROGUE"));
        when(insightsRepository.findById(ID)).thenReturn(Optional.of(existing));

        writer.saveKeepingTexts(ID, Map.of("total_commits", 43), AiTestData.insights(), SYNCED_AT);

        assertThat(saved()).isSameAs(existing);
        assertThat(existing.getStats()).containsEntry("total_commits", 43);
        assertThat(existing.getArchetype()).isEqualTo(Archetype.MONK);
        assertThat(existing.getYearInReview()).isEqualTo("old year");
        assertThat(existing.getCharacterTitle()).isEqualTo("old title");
        assertThat(existing.getAiGeneratedAt()).isEqualTo(writtenAt);
        assertThat(existing.getAiInputs()).containsEntry("archetype", "ROGUE");
        assertThat(user.getLastSyncedAt()).isEqualTo(SYNCED_AT);
    }

    @Test
    void storedTextsReadBackWhatSaveWrote() {
        when(insightsRepository.findById(ID)).thenReturn(Optional.empty());
        writer.save(ID, Map.of(), AiTestData.insights(), TEXTS, SYNCED_AT);
        Insights row = saved();
        when(insightsRepository.findById(ID)).thenReturn(Optional.of(row));

        assertThat(writer.storedTexts(ID)).isEqualTo(
                new Regeneration.Stored(true, SYNCED_AT, AiInputs.of(AiTestData.insights()), false));
    }

    @Test
    void storedTextsOfAUserWithoutARowOrFromBeforeV7() {
        when(insightsRepository.findById(ID)).thenReturn(Optional.empty());
        assertThat(writer.storedTexts(ID)).isEqualTo(Regeneration.Stored.NONE);

        Insights legacy = new Insights(user);
        legacy.setYearInReview("written before V7");
        when(insightsRepository.findById(ID)).thenReturn(Optional.of(legacy));
        assertThat(writer.storedTexts(ID)).isEqualTo(new Regeneration.Stored(true, null, null, false));
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
