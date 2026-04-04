---
breadcrumb: [docs](../index.md) > [docMaintenance](.) > maintenanceGuide
---

# Documentation Maintenance Guide

This file contains standing instructions for AI agents working in this codebase.

## On Every Code Change

1. Identify which source files were modified.
2. Map each modified file to its corresponding docs section using `docs/index.md`.
3. Update those doc files to reflect the change.
4. Log a new entry to `docs/docMaintenance/changelog.md` and `docs/docMaintenance/changelog.json`.
5. Update the `Last Updated` date for any modified doc files in `docs/docMaintenance/documentationProgress.md`.

## Changelog Entry Format

**changelog.md row:**

| session | date | focus | files | summary |
| n/a | YYYY-MM-DD | short description | comma-separated list | one-sentence summary |

**changelog.json entry:**

```json
{
  "session": "n/a",
  "date": "YYYY-MM-DD",
  "focus": "short description",
  "files": ["file1", "file2"],
  "summary": "one-sentence summary"
}
```

## Rules

- Always prepend new changelog entries (newest first).
- Do not modify docs sections that were not affected by the code change.
- If a source file has no corresponding doc section yet, note it in changelog focus as "undocumented".
