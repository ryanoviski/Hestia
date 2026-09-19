-- Household-specific presentation and visibility for shared default categories.
CREATE TABLE category_preferences (
    household_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    name TEXT,
    color TEXT,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    updated_at TEXT NOT NULL,
    PRIMARY KEY (household_id, category_id),
    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

CREATE INDEX idx_category_preferences_household_active
    ON category_preferences(household_id, active);
