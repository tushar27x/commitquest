package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.repository.HeroRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link HeroRepository} in memory, with the same keys as V5: one hero per user, one event per
 * (user, type, day), one settled row per (user, day). The SQL itself is checked against Postgres in an IT.
 */
public class InMemoryHeroRepository extends HeroRepository {

    public final Map<Long, HeroState> states = new ConcurrentHashMap<>();
    public final Map<Long, String> timezones = new ConcurrentHashMap<>();
    public final List<Stored<HeroEvent>> events = new ArrayList<>();
    public final List<Stored<SettledDay>> days = new ArrayList<>();

    public InMemoryHeroRepository() {
        super(null, null, "v2");
    }

    @Override
    public Optional<HeroState> findState(long userId) {
        return Optional.ofNullable(states.get(userId));
    }

    @Override
    public boolean insertState(long userId, HeroState state, Instant now) {
        return states.putIfAbsent(userId, state) == null;
    }

    @Override
    public synchronized boolean updateState(long userId, HeroState state, LocalDate expected, Instant now) {
        HeroState current = states.get(userId);
        if (current == null || !current.lastEvaluatedOn().equals(expected)) {
            return false;
        }
        states.put(userId, state);
        return true;
    }

    @Override
    public synchronized void insertEvents(long userId, List<HeroEvent> newEvents) {
        for (HeroEvent event : newEvents) {
            boolean exists = events.stream().anyMatch(stored -> stored.userId() == userId
                    && stored.value().type() == event.type() && stored.value().onDate().equals(event.onDate()));
            if (!exists) {
                events.add(new Stored<>(userId, event));
            }
        }
    }

    @Override
    public synchronized void insertDays(long userId, List<SettledDay> newDays) {
        for (SettledDay day : newDays) {
            boolean exists = days.stream().anyMatch(stored -> stored.userId() == userId
                    && stored.value().onDate().equals(day.onDate()));
            if (!exists) {
                days.add(new Stored<>(userId, day));
            }
        }
    }

    @Override
    public synchronized List<HeroEvent> findEvents(long userId, LocalDate from, LocalDate to) {
        return events.stream()
                .filter(stored -> stored.userId() == userId)
                .map(Stored::value)
                .filter(event -> !event.onDate().isBefore(from) && !event.onDate().isAfter(to))
                .sorted(Comparator.comparing(HeroEvent::onDate)) // stable: keeps insertion order within a day
                .toList();
    }

    @Override
    public synchronized List<SettledDay> findDays(long userId, LocalDate from, LocalDate to) {
        return days.stream()
                .filter(stored -> stored.userId() == userId)
                .map(Stored::value)
                .filter(day -> !day.onDate().isBefore(from) && !day.onDate().isAfter(to))
                .sorted(Comparator.comparing(SettledDay::onDate))
                .toList();
    }

    @Override
    public List<Candidate> findCandidates(LocalDate before) {
        List<Candidate> candidates = new ArrayList<>();
        states.forEach((id, state) -> {
            if (state.lastEvaluatedOn().isBefore(before)) {
                candidates.add(new Candidate(id, timezones.getOrDefault(id, "UTC"), state.lastEvaluatedOn()));
            }
        });
        candidates.sort(Comparator.comparingLong(Candidate::userId));
        return candidates;
    }

    public synchronized List<HeroEvent> eventsOf(long userId) {
        return events.stream().filter(stored -> stored.userId() == userId).map(Stored::value).toList();
    }

    public synchronized List<SettledDay> daysOf(long userId) {
        return days.stream().filter(stored -> stored.userId() == userId).map(Stored::value).toList();
    }

    public record Stored<T>(long userId, T value) {
    }
}
