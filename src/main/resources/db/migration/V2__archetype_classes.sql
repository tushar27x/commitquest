-- V2: archetypes become fantasy classes scored from coding habits (PR #3 review).
-- Existing rows are mapped to the closest new class; the next sync recomputes them anyway.

ALTER TABLE insights DROP CONSTRAINT insights_archetype_check;

UPDATE insights
SET archetype = CASE archetype
                    WHEN 'BUG_HUNTER'         THEN 'CLERIC'
                    WHEN 'OPEN_SOURCE_HERO'   THEN 'BARD'
                    WHEN 'SOFTWARE_ARCHITECT' THEN 'ARTIFICER'
                    WHEN 'WEEKEND_WARRIOR'    THEN 'ROGUE'
                    WHEN 'CODE_CRUSADER'      THEN 'PALADIN'
                END
WHERE archetype IS NOT NULL;

-- Must list exactly the constants of entity/Archetype.java
ALTER TABLE insights
    ADD CONSTRAINT insights_archetype_check CHECK (archetype IN (
        'PALADIN', 'BARBARIAN', 'WIZARD', 'ARTIFICER',
        'CLERIC', 'BARD', 'MONK', 'ROGUE'));
