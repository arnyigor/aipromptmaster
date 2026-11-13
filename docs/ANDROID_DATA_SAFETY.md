# Android catalog and database safety

Legacy ZIP imports now validate every JSON record and stable ID before touching
the database. Empty archives, duplicate IDs, malformed records and path traversal
are rejected. Cancellation propagates; response bodies are closed. Missing IDs
in a legacy ZIP do not trigger deletions until a versioned completeness contract
is available.

The repository now calls the existing Room DAO transaction, including reading
local records and preserving personal content/favorite state. Remote catalog
flags cannot mark records as personal or favorite. Personal records with the
same ID are protected from remote overwrites and deletion requests.

Exported schemas 4 and 5 are identical; a missing no-op migration 4→5 is now
registered. Destructive fallback is removed. Schemas 1–3 are missing from source;
their historical migration behavior still requires verification.

Validation on Android 11: migration 4→8 preserves a saved personal prompt,
favorite and note; an in-memory Room test verifies protected personal content
and retained favorite during catalog updates. Both instrumentation tests passed.
Six new unit tests cover ZIP safety; all 33 active Android unit tests passed.
Lint and debug APK assembly passed. LLMInteractorTest is entirely commented out
and is not counted as executed chat coverage.
