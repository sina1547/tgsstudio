# SINA TGS Studio 0.4.0 — Project Recovery

## Included
- Preserve the last validated TGS document before replacing a project on save.
- Validate the current document before promoting it to the recovery snapshot.
- Fall back to and restore the previous snapshot when the primary project is missing or invalid.
- Delete the recovery snapshot with its project.
- App metadata: versionName 0.4.0, versionCode 4.

## Scope
This is a single previous-version recovery point, not a multi-entry history or a user-facing recovery browser. Recovery history selection, crash journal metadata, and dedicated corruption/recovery tests remain future work.
