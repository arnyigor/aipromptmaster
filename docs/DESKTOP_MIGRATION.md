# Desktop migration

The supported KMP desktop application now has a working copy under `desktop/`.
Android remains in `app/`; these are independent Gradle roots during migration.

From the repository root:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
Push-Location desktop
./gradlew.bat :shared:compileTestKotlinDesktop :desktopApp:desktopJar
./gradlew.bat :shared:desktopTest
./gradlew.bat :desktopApp:run
Pop-Location
```

Source: `arnyigor/aiprompts`, subtree `aiprompts-kmp`, source HEAD
`b27e8c5802905a8126476b0f258c21cfc57b516f`.
Filtered source history is preserved locally in `codex/desktop-source-history`,
HEAD `91358c9c28a9d0360e10cf681395605881398dcd`. The working copy also preserves
the pre-existing local change to `gradle.properties`; local credentials and caches
are not copied into the application repository.

The original subtree remains available until migration validation is complete.
Do not develop both copies independently. The new copy is the migration target.
No remote branches or releases have been changed. Retain the source history
branch and backup bundle until the local integration branch is published.

Baseline: Android compilation/tests/lint/APK passed. Original Desktop main JAR
built, but test compilation failed on nullable Selenium return values. The new
copy explicitly checks those values before use. Test execution, desktop launch
and installer validation are separate checks.

Validation in the new location: main JAR and all test sources compile.
`desktopTest --tests '*UseCaseTest' --tests '*PromptSynchronizerTest'` discovered
50 tests: 48 passed, 2 skipped. This is a selected offline test set, not a full
suite or an application launch/installer test.

Follow-up validation: createDistributable succeeded with launcher, resources and
bundled runtime. The launcher exposed the AI Prompt Master window. Smoke testing
used a separate user.home under the private backup directory. It revealed a
legacy title-filter/delete bug; the migration copy now upserts all snapshot IDs,
rejects invalid/empty/duplicate-ID snapshots, propagates cancellation and serializes
sync calls. Legacy ZIP missing entries no longer trigger deletion. Batch Room
inserts are atomic. The corrected smoke database retained all 932 catalog prompts.
Desktop Room schemas 1–3 are now retained in Git instead of ignored.
