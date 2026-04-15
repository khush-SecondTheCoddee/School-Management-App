# Results Module: Exams, Marks Workflow, Publishing, and Exports

## 1) Exam model with subject-wise components and grading schema

Exam definitions are stored at `schools/{schoolId}/exams/{examId}` and include:

- `name`: exam name (e.g., Mid Term)
- `subjects[]`:
  - `subjectCode`
  - `components[]` where each component has:
    - `name` (e.g., Theory, Viva, Practical)
    - `maxMarks`
    - `weightage` (must sum to 100 for each subject)
- `gradingSchema[]`:
  - `grade`
  - `minPercentage`

## 2) Teacher marks entry with validation and draft/final states

Endpoint: `POST /v1/schools/:schoolId/exams/:examId/marks`

- `mode = DRAFT` keeps status as `DRAFT`
- `mode = FINAL` pushes status to `PENDING_APPROVAL`

Validation:

- Student id is required.
- Subject entries must match exam model subjects.
- Component entries must match exam model components.
- Obtained marks cannot exceed component max marks.

## 3) Management approval flow for publishing results

Endpoint: `PATCH /v1/schools/:schoolId/results/:resultId/approval`

- `decision = APPROVE`: `PENDING_APPROVAL -> PUBLISHED`
- `decision = REJECT`: `PENDING_APPROVAL -> REJECTED`

Published entries are immutable in security rules.

## 4) Student/parent result views with trend and report card downloads

- Student result history: `GET /v1/schools/:schoolId/students/:studentId/results`
  - Includes a trend array based on recent published exams.
- Report card: `GET /v1/schools/:schoolId/results/:resultId/report-card`
  - Includes `downloads.csv` and `downloads.pdf` links.

## 5) Export endpoints and immutable publish audit trail

- CSV export: `GET /v1/schools/:schoolId/results/:resultId/export.csv`
- PDF export: `GET /v1/schools/:schoolId/results/:resultId/export.pdf`

Every publish event writes an append-only record to:

- `schools/{schoolId}/results_publish_audit/{auditId}`

Each audit event includes a deterministic `publishHash` to support tamper detection during compliance checks.
