package com.tushar27x.insightshub.it;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Compares a real response with a contract fixture by shape: the same keys at every level, the same JSON types,
 * and the enum and format rules of {@code docs/api/contract.md}. Values may differ. Paths look like
 * {@code $.class.chosen} and {@code $.week[].status} (every array element shares one path).
 */
final class ContractShape {

    private static final Path FIXTURES = Path.of("docs/api/fixtures");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static final Set<String> ARCHETYPES = Set.of("PALADIN", "BARBARIAN", "WIZARD", "ARTIFICER", "CLERIC", "BARD",
            "MONK", "ROGUE");
    static final Set<String> WEEKDAYS = Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
            "SUNDAY");
    static final Set<String> EVENT_TYPES = Set.of("LIFE_LOST", "LIFE_GAINED", "LEVEL_UP", "LEVEL_DOWN", "FAINTED",
            "RESTED", "CLASS_CHANGED");
    static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /** ISO-8601 UTC, as the contract says for every Instant. */
    static final Pattern INSTANT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");

    /** One rule for the values at a path: an enum or a format. */
    record Rule(String path, Set<String> allowed, Pattern format) {
        static Rule oneOf(String path, Set<String> allowed) {
            return new Rule(path, allowed, null);
        }

        static Rule format(String path, Pattern format) {
            return new Rule(path, null, format);
        }
    }

    /**
     * @param nullable paths where either side may be null (the contract types them {@code X|null})
     * @param maps     paths whose keys are data, not schema (repo names); only the values' types are compared
     * @param skip     paths checked elsewhere (event details, per type)
     */
    record Spec(Set<String> nullable, Set<String> maps, Set<String> skip, List<Rule> rules) {
    }

    private ContractShape() {
    }

    static JsonNode fixture(String name) {
        try {
            return JSON.readTree(Files.readString(FIXTURES.resolve(name)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode parse(String body) {
        return JSON.readTree(body);
    }

    /** Every difference from the contract; empty when the response fits. */
    static List<String> check(JsonNode actual, JsonNode fixture, Spec spec) {
        List<String> errors = new ArrayList<>();
        compare("$", actual, fixture, spec, errors);
        for (Rule rule : spec.rules()) {
            for (JsonNode value : select(actual, rule.path())) {
                if (value.isNull()) {
                    continue; // nullability is the shape check's job
                }
                String text = value.isString() ? value.stringValue() : value.toString();
                if (rule.allowed() != null && !rule.allowed().contains(text)) {
                    errors.add(rule.path() + ": '" + text + "' is not one of " + new TreeSet<>(rule.allowed()));
                }
                if (rule.format() != null && !(value.isString() && rule.format().matcher(text).matches())) {
                    errors.add(rule.path() + ": '" + text + "' doesn't match " + rule.format());
                }
            }
        }
        return errors;
    }

    private static void compare(String path, JsonNode actual, JsonNode fixture, Spec spec, List<String> errors) {
        if (spec.skip().contains(path)) {
            return;
        }
        if (actual.isNull() || fixture.isNull()) {
            if (actual.isNull() != fixture.isNull() && !spec.nullable().contains(path)) {
                errors.add(path + ": " + kind(actual) + ", the fixture has " + kind(fixture));
            }
            return;
        }
        if (!kind(actual).equals(kind(fixture))) {
            errors.add(path + ": " + kind(actual) + ", the fixture has " + kind(fixture));
            return;
        }
        if (actual.isObject() && spec.maps().contains(path)) {
            JsonNode sample = fixture.values().stream().findFirst().orElse(null);
            for (Map.Entry<String, JsonNode> entry : actual.properties()) {
                if (sample != null) {
                    compare(path + ".*", entry.getValue(), sample, spec, errors);
                }
            }
        } else if (actual.isObject()) {
            Set<String> actualKeys = new TreeSet<>(actual.propertyNames());
            Set<String> fixtureKeys = new TreeSet<>(fixture.propertyNames());
            if (!actualKeys.equals(fixtureKeys)) {
                Set<String> extra = new TreeSet<>(actualKeys);
                extra.removeAll(fixtureKeys);
                Set<String> missing = new TreeSet<>(fixtureKeys);
                missing.removeAll(actualKeys);
                errors.add(path + ": keys not in the contract " + extra + ", missing " + missing);
            }
            for (String key : actualKeys) {
                if (fixtureKeys.contains(key)) {
                    compare(path + "." + key, actual.get(key), fixture.get(key), spec, errors);
                }
            }
        } else if (actual.isArray() && !fixture.isEmpty()) {
            for (JsonNode element : actual) {
                compare(path + "[]", element, fixture.get(0), spec, errors);
            }
        }
    }

    /** Integers and decimals are different kinds: the contract's ints must not come back as 1.0. */
    private static String kind(JsonNode node) {
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isNumber()) {
            return "decimal";
        }
        return node.getNodeType().name().toLowerCase();
    }

    /** The nodes at a path like {@code $.week[].status}. */
    static List<JsonNode> select(JsonNode root, String path) {
        List<JsonNode> nodes = List.of(root);
        for (String part : path.substring(2).split("\\.")) {
            boolean each = part.endsWith("[]");
            String key = each ? part.substring(0, part.length() - 2) : part;
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode node : nodes) {
                JsonNode child = key.isEmpty() ? node : node.get(key);
                if (child == null) {
                    continue;
                }
                if (each) {
                    child.forEach(next::add);
                } else {
                    next.add(child);
                }
            }
            nodes = next;
        }
        return nodes;
    }
}
