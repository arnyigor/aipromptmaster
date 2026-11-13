"""Recover historical migration fixtures from released DEX and the former data module.

No speculative Room JSON/identity hashes are created. SQL is verified against the
v0.1.0 released APK and versions 2/3's committed entities and migration SQL.
"""
import hashlib
import json
import pathlib
import struct
import subprocess
import sys
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
apk = pathlib.Path(sys.argv[1])
out = root / "app/src/androidTest/assets/legacy-schemas"
out.mkdir(parents=True, exist_ok=True)

def source(ref, name):
    path = f"data/src/main/java/com/arny/aipromptmaster/data/db/{name}"
    return subprocess.check_output(["git", "show", f"{ref}:{path}"], cwd=root).decode()

def dex_strings(data):
    count, table = struct.unpack_from("<II", data, 0x38)
    for index in range(count):
        pos = struct.unpack_from("<I", data, table + index * 4)[0]
        while data[pos] & 128:
            pos += 1
        pos += 1
        end = data.index(0, pos)
        yield data[pos:end].decode("utf-8", errors="replace")

with zipfile.ZipFile(apk) as archive:
    strings = [s for name in archive.namelist() if name.startswith("classes") and name.endswith(".dex")
               for s in dex_strings(archive.read(name))]
prompt_sql = next(s for s in strings if s.startswith("CREATE TABLE IF NOT EXISTS `prompts`"))
indices = sorted(s for s in strings if s.startswith("CREATE INDEX IF NOT EXISTS `index_prompts_"))
assert len(indices) == 5
assert "prompt_variants_json" not in prompt_sql
base = [prompt_sql, *indices]
for version, ref in [(1, "v0.1.0"), (2, "29a6e60"), (3, "2a2672c")]:
    database_source = source(ref, "AppDatabase.kt")
    assert f"version = {version}" in database_source
    prompt_source = source(ref, "entities/PromptEntity.kt")
    assert "prompt_variants_json" not in prompt_source
    statements = list(base)
    if version >= 2:
        conversation_source = source(ref, "entities/ConversationEntity.kt")
        message_source = source(ref, "entities/MessageEntity.kt")
        assert "attachments" not in message_source
        assert "val conversationId: String" in message_source
        statements.extend([
            "CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL" +
            (", `systemPrompt` TEXT" if version == 3 else "") + ", PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)",
        ])
        assert ("val systemPrompt: String?" in conversation_source) == (version == 3)
    (out / f"{version}.sql").write_text(";\n".join(statements) + ";\n", encoding="utf-8")

evidence = {
    "apk": apk.name,
    "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "apkTag": "v0.1.0",
    "sources": {
        str(v): {"ref": ref, "databaseSourceSha256": hashlib.sha256(source(ref, "AppDatabase.kt").encode()).hexdigest()}
        for v, ref in [(1, "v0.1.0"), (2, "29a6e60"), (3, "2a2672c")]
    },
    "method": "Version 1 CREATE TABLE and indexes extracted from released DEX; versions 2/3 verified against historical entity definitions. No identity hashes invented.",
}
(out / "provenance.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Recovered SQL fixtures 1, 2, 3 from released APK and historical sources")
