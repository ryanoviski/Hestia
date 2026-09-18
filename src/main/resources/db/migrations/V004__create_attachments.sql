CREATE TABLE attachments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    household_id INTEGER NOT NULL,
    transaction_id INTEGER,
    profile_id INTEGER,
    document_type TEXT NOT NULL CHECK (document_type IN ('RECEIPT','PAYSLIP','INVOICE','OTHER')),
    description TEXT CHECK (description IS NULL OR length(description) <= 300),
    original_filename TEXT NOT NULL CHECK (length(trim(original_filename)) BETWEEN 1 AND 255),
    storage_key TEXT NOT NULL UNIQUE CHECK (storage_key NOT LIKE '%..%' AND storage_key NOT LIKE '/%' AND storage_key NOT LIKE '\\%'),
    media_type TEXT NOT NULL CHECK (media_type IN ('application/pdf','image/png','image/jpeg')),
    file_extension TEXT NOT NULL CHECK (file_extension IN ('pdf','png','jpg','jpeg')),
    size_bytes INTEGER NOT NULL CHECK (size_bytes > 0),
    sha256 TEXT NOT NULL CHECK (length(sha256) = 64),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE RESTRICT,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE RESTRICT,
    FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE RESTRICT,
    CHECK ((transaction_id IS NOT NULL AND profile_id IS NULL) OR
           (transaction_id IS NULL AND profile_id IS NOT NULL))
);

CREATE INDEX idx_attachments_household_created ON attachments(household_id, created_at);
CREATE INDEX idx_attachments_transaction ON attachments(transaction_id);
CREATE INDEX idx_attachments_profile ON attachments(profile_id);
CREATE INDEX idx_attachments_type ON attachments(household_id, document_type);
