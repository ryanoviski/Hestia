CREATE TABLE recurring_expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    household_id INTEGER NOT NULL,
    profile_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    description TEXT NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 150),
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    first_due_date TEXT NOT NULL,
    end_date TEXT,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 1000),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE RESTRICT,
    FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE RESTRICT,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CHECK (end_date IS NULL OR end_date >= first_due_date)
);

CREATE INDEX idx_recurring_expenses_household_active
    ON recurring_expenses(household_id, active);
CREATE INDEX idx_recurring_expenses_profile ON recurring_expenses(profile_id);
CREATE INDEX idx_recurring_expenses_category ON recurring_expenses(category_id);

CREATE TABLE recurring_expense_occurrences (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recurring_expense_id INTEGER NOT NULL,
    transaction_id INTEGER NOT NULL UNIQUE,
    reference_month TEXT NOT NULL CHECK (reference_month GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]'),
    customized INTEGER NOT NULL DEFAULT 0 CHECK (customized IN (0, 1)),
    created_at TEXT NOT NULL,
    FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expenses(id) ON DELETE RESTRICT,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE RESTRICT,
    UNIQUE (recurring_expense_id, reference_month)
);

CREATE INDEX idx_recurring_occurrences_rule
    ON recurring_expense_occurrences(recurring_expense_id, reference_month);

CREATE TABLE installment_plans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    household_id INTEGER NOT NULL,
    profile_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    description TEXT NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 150),
    total_amount_cents INTEGER NOT NULL CHECK (total_amount_cents > 0),
    installment_count INTEGER NOT NULL CHECK (installment_count BETWEEN 1 AND 120),
    first_due_date TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    notes TEXT CHECK (notes IS NULL OR length(notes) <= 1000),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE RESTRICT,
    FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE RESTRICT,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);

CREATE INDEX idx_installment_plans_household ON installment_plans(household_id);
CREATE INDEX idx_installment_plans_profile ON installment_plans(profile_id);
CREATE INDEX idx_installment_plans_category ON installment_plans(category_id);

CREATE TABLE installments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    installment_plan_id INTEGER NOT NULL,
    transaction_id INTEGER NOT NULL UNIQUE,
    installment_number INTEGER NOT NULL CHECK (installment_number > 0),
    planned_amount_cents INTEGER NOT NULL CHECK (planned_amount_cents > 0),
    created_at TEXT NOT NULL,
    FOREIGN KEY (installment_plan_id) REFERENCES installment_plans(id) ON DELETE RESTRICT,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE RESTRICT,
    UNIQUE (installment_plan_id, installment_number)
);

CREATE INDEX idx_installments_plan ON installments(installment_plan_id, installment_number);
