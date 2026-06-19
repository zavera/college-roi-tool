-- Adds W-2 documents for all 5 demo students and Schedule C / SE for Priya Sharma.
-- V9 seeded one document (1040) per student. This adds the supporting forms.
-- All inserts guard with NOT EXISTS so re-runs are safe.

-- ── Emma Richardson — W-2 ──────────────────────────────────────────────────
INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/emma-richardson-w2-2023.pdf',
       '/api/demo/document/emma-richardson-w2-2023.pdf',
       '2023_W2_JohnsHopkins.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Emma' AND s.last_name = 'Richardson'
  AND NOT EXISTS (
    SELECT 1 FROM student_documents d
    WHERE d.student_id = s.id AND d.blob_name = 'demo/emma-richardson-w2-2023.pdf');

-- ── Marcus Johnson — W-2 ───────────────────────────────────────────────────
INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/marcus-johnson-w2-2023.pdf',
       '/api/demo/document/marcus-johnson-w2-2023.pdf',
       '2023_W2_Target.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Marcus' AND s.last_name = 'Johnson'
  AND NOT EXISTS (
    SELECT 1 FROM student_documents d
    WHERE d.student_id = s.id AND d.blob_name = 'demo/marcus-johnson-w2-2023.pdf');

-- ── Sofia Ramirez — W-2 ────────────────────────────────────────────────────
INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/sofia-ramirez-w2-2023.pdf',
       '/api/demo/document/sofia-ramirez-w2-2023.pdf',
       '2023_W2_BoozAllen.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Sofia' AND s.last_name = 'Ramirez'
  AND NOT EXISTS (
    SELECT 1 FROM student_documents d
    WHERE d.student_id = s.id AND d.blob_name = 'demo/sofia-ramirez-w2-2023.pdf');

-- ── Aiden Park — W-2 ───────────────────────────────────────────────────────
INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/aiden-park-w2-2023.pdf',
       '/api/demo/document/aiden-park-w2-2023.pdf',
       '2023_W2_MontgomeryCounty.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Aiden' AND s.last_name = 'Park'
  AND NOT EXISTS (
    SELECT 1 FROM student_documents d
    WHERE d.student_id = s.id AND d.blob_name = 'demo/aiden-park-w2-2023.pdf');

-- ── Priya Sharma — W-2 ─────────────────────────────────────────────────────
INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/priya-sharma-w2-2023.pdf',
       '/api/demo/document/priya-sharma-w2-2023.pdf',
       '2023_W2_Leidos.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Priya' AND s.last_name = 'Sharma'
  AND NOT EXISTS (
    SELECT 1 FROM student_documents d
    WHERE d.student_id = s.id AND d.blob_name = 'demo/priya-sharma-w2-2023.pdf');

-- ── Priya Sharma — Schedule C ──────────────────────────────────────────────
INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/priya-sharma-schedule-c-2023.pdf',
       '/api/demo/document/priya-sharma-schedule-c-2023.pdf',
       '2023_ScheduleC.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Priya' AND s.last_name = 'Sharma'
  AND NOT EXISTS (
    SELECT 1 FROM student_documents d
    WHERE d.student_id = s.id AND d.blob_name = 'demo/priya-sharma-schedule-c-2023.pdf');

-- ── Priya Sharma — Schedule SE ─────────────────────────────────────────────
INSERT INTO student_documents (student_id, blob_name, blob_url, original_filename, uploaded_at, active)
SELECT s.id,
       'demo/priya-sharma-schedule-se-2023.pdf',
       '/api/demo/document/priya-sharma-schedule-se-2023.pdf',
       '2023_ScheduleSE.pdf',
       NOW(), TRUE
FROM students s JOIN app_users u ON u.id = s.user_id
WHERE u.email = 'demo@callistotech.org' AND s.first_name = 'Priya' AND s.last_name = 'Sharma'
  AND NOT EXISTS (
    SELECT 1 FROM student_documents d
    WHERE d.student_id = s.id AND d.blob_name = 'demo/priya-sharma-schedule-se-2023.pdf');
