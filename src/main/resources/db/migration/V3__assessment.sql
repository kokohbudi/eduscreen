-- Penerbitan dan pengerjaan: Assignment, ExamSession, snapshot, jawaban, dan Result.
--
--   ADR-0002  finalisasi terjadi saat sesi diakses, tanpa scheduler
--   TC-18/19  balapan finalisasi dijaga kunci pesimistis; unique(session_id) adalah jaring terakhir
--   TC-16     session_answer berbentuk tetap, jadi kolom biasa — bukan JSONB
--   TC-36     setiap tabel membawa client_id yang disaring eksplisit di repository
--   BR-T01    seluruh waktu UTC

-- ---------------------------------------------------------------------------
-- assignment — satu Assignment menyasar tepat satu Ruangan (BR-M02)
--
-- Menerbitkan ke tiga Ruangan menghasilkan tiga baris. UI boleh menyediakan tindakan
-- borongan; entitasnya tetap tiga, sehingga waktu, penutupan, dan rekap tiap Ruangan
-- berdiri sendiri.
-- ---------------------------------------------------------------------------
create table assignment (
    id                     uuid        primary key,
    client_id              uuid        not null references client (id),
    exercise_id            uuid        not null references exercise (id),
    ruangan_id             uuid        not null references ruangan (id),
    published_by           uuid        not null references app_user (id),
    mode                   text        not null,
    status                 text        not null default 'DRAFT',
    title                  text        not null,
    -- Wajib untuk QUIZ, boleh kosong untuk PRACTICE (BR-M03).
    timer_duration_minutes int,
    expires_at             timestamptz not null,
    -- Diabaikan untuk PRACTICE: di sana Attempt tidak terbatas (BR-M06).
    max_attempts           int         not null default 1,
    shuffle_questions      boolean     not null default false,
    shuffle_options        boolean     not null default false,
    reveal_answers_at      text        not null default 'AFTER_SUBMIT',
    published_at           timestamptz,
    closed_at              timestamptz,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),

    constraint assignment_mode_valid   check (mode in ('QUIZ', 'PRACTICE')),
    constraint assignment_status_valid check (status in ('DRAFT', 'PUBLISHED', 'CLOSED')),
    constraint assignment_reveal_valid check (reveal_answers_at in ('AFTER_SUBMIT', 'AFTER_EXPIRATION')),
    constraint assignment_max_attempts_positive check (max_attempts >= 1),
    constraint assignment_timer_positive check (timer_duration_minutes is null or timer_duration_minutes > 0),
    -- QUIZ tanpa Timer adalah ujian tanpa batas pengerjaan; ditolak di database, bukan
    -- hanya di service (BR-M03).
    constraint assignment_quiz_needs_timer check (
        mode <> 'QUIZ' or status = 'DRAFT' or timer_duration_minutes is not null
    )
);

create index assignment_by_ruangan on assignment (ruangan_id, status, expires_at);
create index assignment_by_client on assignment (client_id, status);
create index assignment_by_exercise on assignment (exercise_id);

-- ---------------------------------------------------------------------------
-- exam_session — lahir hanya saat Siswa menekan Mulai (BR-S01)
--
-- effective_deadline DIBEKUKAN saat sesi lahir (BR-T04). Global Expiration selalu
-- memangkas Timer, dan pemangkasan itu terlihat sejak detik pertama, bukan sebagai
-- pemutusan mendadak di tengah jalan.
-- ---------------------------------------------------------------------------
create table exam_session (
    id                 uuid        primary key,
    client_id          uuid        not null references client (id),
    assignment_id      uuid        not null references assignment (id),
    student_id         uuid        not null references app_user (id),
    attempt_number     int         not null default 1,
    status             text        not null default 'IN_PROGRESS',
    started_at         timestamptz not null default now(),
    effective_deadline timestamptz not null,
    finalized_at       timestamptz,
    terminal_reason    text,

    constraint exam_session_status_valid check (status in ('IN_PROGRESS', 'COMPLETED', 'EXPIRED')),
    constraint exam_session_reason_valid check (
        terminal_reason is null
        or terminal_reason in ('MANUAL_SUBMIT', 'TIMER_TIMEOUT', 'EXPIRATION_REACHED')
    ),
    constraint exam_session_attempt_positive check (attempt_number >= 1),
    -- Sesi terminal wajib membawa sebabnya. Tanpa constraint ini, sesi yang berhenti
    -- tanpa alasan tercatat akan lolos dan tidak bisa dijelaskan saat nilai disengketakan.
    constraint exam_session_terminal_complete check (
        (status = 'IN_PROGRESS' and terminal_reason is null and finalized_at is null)
        or (status <> 'IN_PROGRESS' and terminal_reason is not null and finalized_at is not null)
    )
);

create unique index exam_session_attempt_unique
    on exam_session (assignment_id, student_id, attempt_number);
create index exam_session_by_assignment_status on exam_session (assignment_id, status);
create index exam_session_by_student on exam_session (student_id, assignment_id);

-- ---------------------------------------------------------------------------
-- session_question — snapshot beku (BR-S02)
--
-- Tidak pernah berubah setelah dibuat, termasuk ketika Siswa kembali setelah terputus
-- dan ketika soal aslinya di-soft-delete di tengah ujian (BR-Q04).
-- ---------------------------------------------------------------------------
create table session_question (
    id           uuid        primary key,
    session_id   uuid        not null references exam_session (id),
    question_id  uuid        not null references question (id),
    position     int         not null,
    -- Urutan Option hasil pengacakan untuk sesi ini. Array, bukan tabel tersendiri:
    -- isinya urutan murni dan tidak pernah di-query per elemen.
    option_order uuid[]      not null default '{}',
    -- Terisi pada Practice saat jawaban pertama dikirim (BR-S07).
    locked_at    timestamptz,

    constraint session_question_position_positive check (position >= 0)
);

create unique index session_question_unique on session_question (session_id, question_id);
create index session_question_ordered on session_question (session_id, position);

-- ---------------------------------------------------------------------------
-- session_answer — kunci alami upsert auto-save (TC-20)
--
-- Satu baris per SessionQuestion. Kiriman ulang berisi jawaban identik adalah no-op:
-- antrean coba-ulang di klien menjamin server akan menerima kiriman ganda, dan server
-- yang menolaknya mengubah mekanisme pemulihan menjadi sumber kerusakan.
-- ---------------------------------------------------------------------------
create table session_answer (
    id                  uuid        primary key,
    session_question_id uuid        not null references session_question (id),
    selected_option_id  uuid        references question_option (id),
    essay_text          text,
    -- Dihitung saat disimpan untuk MCQ; null untuk essay sampai Guru menilai.
    is_correct          boolean,
    essay_score         int,
    answered_at         timestamptz not null default now(),

    constraint session_answer_essay_score_range check (
        essay_score is null or (essay_score >= 0 and essay_score <= 100)
    )
);

create unique index session_answer_unique on session_answer (session_question_id);

-- ---------------------------------------------------------------------------
-- result — skor disimpan, tidak dihitung ulang saat dibaca (BR-T09)
--
-- Angka historis tidak boleh bergeser bila aturan skoring berubah di rilis berikutnya.
-- ---------------------------------------------------------------------------
create table result (
    id                uuid        primary key,
    session_id        uuid        not null references exam_session (id),
    client_id         uuid        not null references client (id),
    status            text        not null,
    kind              text        not null,
    total_questions   int         not null,
    correct_count     int         not null default 0,
    incorrect_count   int         not null default 0,
    unanswered_count  int         not null default 0,
    score             numeric(6,4) not null default 0,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),

    constraint result_status_valid check (status in ('PENDING_REVIEW', 'FINAL')),
    constraint result_kind_valid   check (kind in ('GRADED', 'PRACTICE')),
    constraint result_score_range  check (score >= 0 and score <= 1),
    -- Practice tidak pernah memuat essay, jadi Result-nya tidak pernah menunggu penilaian
    -- (BR-R01, BR-C09).
    constraint result_practice_is_final check (kind <> 'PRACTICE' or status = 'FINAL')
);

-- TC-19: kunci pesimistis mencegah balapan; constraint ini memastikan kelalaian di jalur
-- mana pun tidak sanggup melanggar aturan.
create unique index result_session_unique on result (session_id);
create index result_by_client on result (client_id, status);

-- ---------------------------------------------------------------------------
-- stored_image — metadata gambar soal; isinya di FileStoragePort (TC-28)
--
-- Tabel ini ada supaya GET /gambar/{id} bisa memeriksa client_id sebelum melayani
-- berkas (TC-26). Tanpanya, empat lapis anti-IDOR bisa dilewati dengan membagikan
-- satu URL .png: soal ujian besok bocor lewat berkas, bukan lewat endpoint Session.
-- ---------------------------------------------------------------------------
create table stored_image (
    id           uuid        primary key,
    client_id    uuid        references client (id),
    file_id      uuid        not null,
    content_type text        not null,
    byte_size    int         not null,
    uploaded_by  uuid        not null references app_user (id),
    created_at   timestamptz not null default now(),

    constraint stored_image_type_valid check (content_type in ('image/png', 'image/jpeg')),
    constraint stored_image_size_positive check (byte_size > 0)
);

create index stored_image_by_client on stored_image (client_id);
