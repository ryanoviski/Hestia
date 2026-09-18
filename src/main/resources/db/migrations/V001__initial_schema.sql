-- Hestia initial database schema. Audit timestamps use ISO-8601 UTC text.
CREATE TABLE households (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE profiles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    household_id INTEGER NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    profile_type TEXT NOT NULL CHECK (profile_type IN ('PERSON', 'SHARED')),
    color TEXT,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE RESTRICT
);

CREATE INDEX idx_profiles_household_active ON profiles(household_id, active);
CREATE UNIQUE INDEX uq_profiles_household_name ON profiles(household_id, name COLLATE NOCASE);

CREATE TABLE categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    household_id INTEGER,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    category_type TEXT NOT NULL CHECK (category_type IN ('INCOME', 'EXPENSE')),
    color TEXT,
    icon TEXT,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE CASCADE
);

CREATE INDEX idx_categories_household_type ON categories(household_id, category_type, active);
CREATE UNIQUE INDEX uq_default_categories_name_type
    ON categories(name COLLATE NOCASE, category_type) WHERE household_id IS NULL;
CREATE UNIQUE INDEX uq_household_categories_name_type
    ON categories(household_id, name COLLATE NOCASE, category_type) WHERE household_id IS NOT NULL;
