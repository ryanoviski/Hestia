CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    household_id INTEGER NOT NULL,
    profile_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    description TEXT NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 150),
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    reference_date TEXT NOT NULL,
    due_date TEXT,
    settlement_date TEXT,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'SETTLED', 'CANCELLED')),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 1000),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE RESTRICT,
    FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE RESTRICT,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CHECK ((status = 'SETTLED' AND settlement_date IS NOT NULL)
        OR (status != 'SETTLED' AND settlement_date IS NULL))
);

CREATE INDEX idx_transactions_household_reference ON transactions(household_id, reference_date);
CREATE INDEX idx_transactions_household_type ON transactions(household_id, transaction_type);
CREATE INDEX idx_transactions_household_status ON transactions(household_id, status);
CREATE INDEX idx_transactions_profile ON transactions(profile_id);
CREATE INDEX idx_transactions_category ON transactions(category_id);
CREATE INDEX idx_transactions_due_date ON transactions(household_id, due_date);
