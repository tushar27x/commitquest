-- V7 (B11): the AI texts are written once at a new user's first login, then only by the daily job when the data
-- they describe moved. For that the texts keep their own timestamp (insights.updated_at moves on every sync) and
-- the inputs they were written from (ai/AiInputs as JSON: class, scores, totals, top repos and languages).
-- Rows from before V7 keep both NULL: the next daily run rewrites their texts once and fills them in.
ALTER TABLE insights
    ADD COLUMN ai_generated_at TIMESTAMPTZ,
    ADD COLUMN ai_inputs       JSONB,
    ADD COLUMN ai_fallback     BOOLEAN NOT NULL DEFAULT FALSE, -- a field holds a static fallback: rewrite next run
    ADD CONSTRAINT insights_ai_inputs_with_time CHECK ((ai_generated_at IS NULL) = (ai_inputs IS NULL));
