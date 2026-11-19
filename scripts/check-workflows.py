#!/usr/bin/env python3
"""Parses every workflow file. A syntax error here is invisible locally and fatal on
GitHub: the workflow is still listed, but its triggers are silently dropped — which is
exactly how `workflow_dispatch` went missing once (a `run:` value that began with a
double quote turns into a quoted scalar with trailing text)."""

import glob
import sys

import yaml


def main() -> int:
    files = sorted(glob.glob(".github/workflows/*.yml") + glob.glob(".github/workflows/*.yaml"))
    if not files:
        print("FAIL: no workflow files found")
        return 1

    failures = []
    for path in files:
        try:
            with open(path, encoding="utf-8") as fh:
                doc = yaml.safe_load(fh)
        except Exception as exc:  # noqa: BLE001 - report any parser error verbatim
            failures.append(f"{path}: {exc}")
            continue
        if not isinstance(doc, dict) or "jobs" not in doc:
            failures.append(f"{path}: no `jobs:` mapping")
            continue
        # PyYAML (YAML 1.1) reads the bare key `on:` as boolean True. GitHub reads it as "on".
        triggers = doc.get("on", doc.get(True))
        if not triggers:
            failures.append(f"{path}: no triggers under `on:`")
            continue
        print(f"OK  {path}  triggers={sorted(triggers) if isinstance(triggers, dict) else triggers}")

    if failures:
        print("\nFAIL: workflow files do not parse")
        for line in failures:
            print(f"  {line}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
