package com.tushar27x.insightshub.ai;

/** Which of a provider's models a task needs. */
public enum ModelTier {
    /** The provider's main model: reviews, year in review, character sheet. */
    STANDARD,
    /** A cheaper model for simple tasks (repo summaries). */
    FAST
}
