# Checks and releases

`checks.yml` checks Android and Windows Desktop independently. Desktop compiles
all test sources and runs the selected offline catalog/use-case suite; live
Selenium/manual tests remain separate. Portable artifacts contain the complete
launcher, app resources and bundled runtime directory.

`release.yml` checks out the exact existing tag for both automatic and manual
runs. Unit tests and lint run on that commit. Signing uses the existing secret
names and key. The APK signature is verified; source commit and checksum are
published alongside the APK. No version.properties edits or pushes happen in
release jobs. `sync-version.yml` is removed. Create release tags explicitly.

Local checks passed as recorded in the migration checklist. Remote workflow
runs and signed release publication have not yet been executed. Published tags
whose source predates this migration will not contain the new workflows.
