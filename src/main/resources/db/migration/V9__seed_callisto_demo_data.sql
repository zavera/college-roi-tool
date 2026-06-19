-- Callisto Tech demo data seed.
-- Only runs in prod (spring.flyway.enabled=true). Flyway is disabled locally.
-- All inserts are idempotent via ON CONFLICT DO NOTHING.

-- ── Demo counselor ─────────────────────────────────────────────────────────
-- password_hash is NULL here; DataInitializer sets it at startup via BCrypt.
INSERT INTO app_users (email, name, provider, subscription_active, created_at)
VALUES ('demo@callistotech.org', 'Callisto Demo', 'local', TRUE, NOW())
ON CONFLICT (email) DO NOTHING;

-- ── Default institution ────────────────────────────────────────────────────
INSERT INTO institutions (name, code, active, created_at)
VALUES ('Callisto Tech', 'callisto-tech', TRUE, NOW())
ON CONFLICT (code) DO NOTHING;

-- ── Link counselor → institution ───────────────────────────────────────────
INSERT INTO user_institutions (user_id, institution_id, active, joined_at)
SELECT u.id, i.id, TRUE, NOW()
FROM app_users u
CROSS JOIN institutions i
WHERE u.email = 'demo@callistotech.org'
  AND i.code  = 'callisto-tech'
ON CONFLICT DO NOTHING;

-- ── Demo students ──────────────────────────────────────────────────────────

INSERT INTO students (user_id, first_name, last_name, date_of_birth, created_at)
SELECT u.id, 'Emma', 'Richardson', '2006-03-14', NOW()
FROM app_users u WHERE u.email = 'demo@callistotech.org'
ON CONFLICT ON CONSTRAINT uq_student_per_user DO NOTHING;

INSERT INTO students (user_id, first_name, last_name, date_of_birth, created_at)
SELECT u.id, 'Marcus', 'Johnson', '2002-08-22', NOW()
FROM app_users u WHERE u.email = 'demo@callistotech.org'
ON CONFLICT ON CONSTRAINT uq_student_per_user DO NOTHING;

INSERT INTO students (user_id, first_name, last_name, date_of_birth, created_at)
SELECT u.id, 'Sofia', 'Ramirez', '2007-01-09', NOW()
FROM app_users u WHERE u.email = 'demo@callistotech.org'
ON CONFLICT ON CONSTRAINT uq_student_per_user DO NOTHING;

INSERT INTO students (user_id, first_name, last_name, date_of_birth, created_at)
SELECT u.id, 'Aiden', 'Park', '2005-11-30', NOW()
FROM app_users u WHERE u.email = 'demo@callistotech.org'
ON CONFLICT ON CONSTRAINT uq_student_per_user DO NOTHING;

INSERT INTO students (user_id, first_name, last_name, date_of_birth, created_at)
SELECT u.id, 'Priya', 'Sharma', '2004-06-17', NOW()
FROM app_users u WHERE u.email = 'demo@callistotech.org'
ON CONFLICT ON CONSTRAINT uq_student_per_user DO NOTHING;

-- ── Link all demo students → Callisto Tech ─────────────────────────────────
INSERT INTO student_institutions (student_id, institution_id, active)
SELECT s.id, i.id, TRUE
FROM students s
JOIN app_users u ON u.id = s.user_id
CROSS JOIN institutions i
WHERE u.email = 'demo@callistotech.org'
  AND i.code  = 'callisto-tech'
ON CONFLICT DO NOTHING;

-- ── Student documents (placeholder blob URLs — no Azure needed) ────────────

INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/emma-richardson-1040-2023.pdf',
       '/api/demo/document/emma-richardson-1040-2023.pdf',
       '2023_1040.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Emma' AND s.last_name = 'Richardson'
  AND NOT EXISTS (SELECT 1 FROM student_documents d WHERE d.student_id = s.id);

INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/marcus-johnson-1040-2023.pdf',
       '/api/demo/document/marcus-johnson-1040-2023.pdf',
       '2023_1040.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Marcus' AND s.last_name = 'Johnson'
  AND NOT EXISTS (SELECT 1 FROM student_documents d WHERE d.student_id = s.id);

INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/sofia-ramirez-1040-2023.pdf',
       '/api/demo/document/sofia-ramirez-1040-2023.pdf',
       '2023_1040.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Sofia' AND s.last_name = 'Ramirez'
  AND NOT EXISTS (SELECT 1 FROM student_documents d WHERE d.student_id = s.id);

INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/aiden-park-1040-2023.pdf',
       '/api/demo/document/aiden-park-1040-2023.pdf',
       '2023_1040.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Aiden' AND s.last_name = 'Park'
  AND NOT EXISTS (SELECT 1 FROM student_documents d WHERE d.student_id = s.id);

INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/priya-sharma-1040-2023.pdf',
       '/api/demo/document/priya-sharma-1040-2023.pdf',
       '2023_1040.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Priya' AND s.last_name = 'Sharma'
  AND NOT EXISTS (SELECT 1 FROM student_documents d WHERE d.student_id = s.id);

-- ── KV extracts (realistic 2023 tax / financial aid data) ──────────────────
-- Emma Richardson: middle-income dependent, 3-person household, parents filing jointly
INSERT INTO document_kv_extracts (document_id, kv_json, extracted_at)
SELECT d.id, '{
  "Tax Year": "2023",
  "Filing Status": "Married Filing Jointly",
  "Adjusted Gross Income": "52400",
  "Total Wages and Salaries": "48200",
  "Interest Income": "320",
  "Federal Income Tax Withheld": "4100",
  "Number of Dependents": "3",
  "Child Tax Credit": "2000",
  "Education Savings Account Balance": "8500",
  "Enrollment Status": "Full-time",
  "Major": "Nursing"
}', NOW()
FROM student_documents d
JOIN students s ON s.id = d.student_id
JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Emma' AND s.last_name = 'Richardson'
ON CONFLICT ON CONSTRAINT document_kv_extracts_document_id_key DO NOTHING;

-- Marcus Johnson: independent student, low income, first-gen, self-supporting
INSERT INTO document_kv_extracts (document_id, kv_json, extracted_at)
SELECT d.id, '{
  "Tax Year": "2023",
  "Filing Status": "Single",
  "Adjusted Gross Income": "18200",
  "Total Wages and Salaries": "17800",
  "Federal Income Tax Withheld": "890",
  "Earned Income Credit": "540",
  "Interest Income": "0",
  "Enrollment Status": "Full-time",
  "Major": "Computer Science",
  "First Generation College Student": "Yes",
  "Number of Dependents": "0"
}', NOW()
FROM student_documents d
JOIN students s ON s.id = d.student_id
JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Marcus' AND s.last_name = 'Johnson'
ON CONFLICT ON CONSTRAINT document_kv_extracts_document_id_key DO NOTHING;

-- Sofia Ramirez: high-income dependent, limited need-based aid, investment assets
INSERT INTO document_kv_extracts (document_id, kv_json, extracted_at)
SELECT d.id, '{
  "Tax Year": "2023",
  "Filing Status": "Married Filing Jointly",
  "Adjusted Gross Income": "145000",
  "Total Wages and Salaries": "130000",
  "Interest Income": "2100",
  "Dividend Income": "4800",
  "Capital Gains": "8200",
  "Federal Income Tax Withheld": "24200",
  "Investment Portfolio Value": "185000",
  "529 Plan Balance": "42000",
  "Number of Dependents": "2",
  "Enrollment Status": "Full-time",
  "Major": "Pre-Law"
}', NOW()
FROM student_documents d
JOIN students s ON s.id = d.student_id
JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Sofia' AND s.last_name = 'Ramirez'
ON CONFLICT ON CONSTRAINT document_kv_extracts_document_id_key DO NOTHING;

-- Aiden Park: custodial parent only, divorced household, professional judgment opportunity
INSERT INTO document_kv_extracts (document_id, kv_json, extracted_at)
SELECT d.id, '{
  "Tax Year": "2023",
  "Filing Status": "Single",
  "Adjusted Gross Income": "38500",
  "Total Wages and Salaries": "36200",
  "Federal Income Tax Withheld": "3100",
  "Interest Income": "85",
  "Child Support Received": "4800",
  "Number of Dependents": "1",
  "Housing Allowance": "12000",
  "Enrollment Status": "Full-time",
  "Major": "Business Administration",
  "Special Circumstance": "Divorced parents — custodial parent only on FAFSA"
}', NOW()
FROM student_documents d
JOIN students s ON s.id = d.student_id
JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Aiden' AND s.last_name = 'Park'
ON CONFLICT ON CONSTRAINT document_kv_extracts_document_id_key DO NOTHING;

-- Priya Sharma: moderate income, STEM focus, small business income, first-gen
INSERT INTO document_kv_extracts (document_id, kv_json, extracted_at)
SELECT d.id, '{
  "Tax Year": "2023",
  "Filing Status": "Married Filing Jointly",
  "Adjusted Gross Income": "76300",
  "Total Wages and Salaries": "71500",
  "Federal Income Tax Withheld": "9800",
  "Interest Income": "410",
  "Business Income": "4800",
  "Self Employment Tax": "680",
  "Education Credits": "2500",
  "529 Plan Balance": "15000",
  "Number of Dependents": "2",
  "Enrollment Status": "Full-time",
  "Major": "Electrical Engineering",
  "First Generation College Student": "Yes"
}', NOW()
FROM student_documents d
JOIN students s ON s.id = d.student_id
JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Priya' AND s.last_name = 'Sharma'
ON CONFLICT ON CONSTRAINT document_kv_extracts_document_id_key DO NOTHING;
