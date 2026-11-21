# SINA TGS Studio — Project Recovery

## Included
- Preserve the last validated TGS document before each save replaces the current project.
- Validate the existing document before promoting it to a recovery snapshot, so a damaged file does not overwrite a good snapshot.
- Fall back to the previous snapshot when the primary project file is missing or cannot be parsed, and restore the recovered bytes to the primary path.
- Remove the recovery snapshot when its project is deleted.

## Scope
This is a single previous-version recovery point, not a multi-entry history or a user-facing recovery browser. Recovery history selection, crash journal metadata, and dedicated corruption/recovery tests remain future work.
