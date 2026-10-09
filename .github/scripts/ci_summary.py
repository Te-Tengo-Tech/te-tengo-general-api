#!/usr/bin/env python3
"""Markdown summaries for the CI job page ($GITHUB_STEP_SUMMARY).

  ci_summary.py junit  <title> <test-results-dir>   test counts of one Gradle test lane
  ci_summary.py jacoco <title> <jacoco-report.xml>  coverage totals and coverage per module
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

BASE_PACKAGE = "tech/tetengo/api/"
COUNTERS = ("LINE", "BRANCH", "INSTRUCTION", "METHOD")


def junit(title: str, directory: Path) -> None:
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    seconds = 0.0
    failed: list[str] = []
    for report in sorted(directory.glob("*.xml")):
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, "0"))
        seconds += float(suite.get("time", "0"))
        for case in suite.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                failed.append(f"{case.get('classname')} > {case.get('name')}")
    passed = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    print(f"## {title}")
    print()
    print("| Tests | Passed | Failed | Skipped | Time |")
    print("|---:|---:|---:|---:|---:|")
    print(
        f"| {totals['tests']} | {passed} | {totals['failures'] + totals['errors']} "
        f"| {totals['skipped']} | {seconds:.1f} s |"
    )
    if failed:
        print()
        print("Failed:")
        for name in failed:
            print(f"- `{name}`")
    print()


def ratio(covered: int, missed: int) -> str:
    total = covered + missed
    return f"{100 * covered / total:.1f} %" if total else "n/a"


def jacoco(title: str, report: Path) -> None:
    root = ET.parse(report).getroot()
    totals = {c.get("type"): c for c in root.findall("counter")}
    modules: dict[str, dict[str, list[int]]] = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    for package in root.findall("package"):
        name = package.get("name", "")
        module = (name + "/").removeprefix(BASE_PACKAGE).split("/")[0] or "(root)"
        for counter in package.findall("counter"):
            pair = modules[module][counter.get("type", "")]
            pair[0] += int(counter.get("covered", "0"))
            pair[1] += int(counter.get("missed", "0"))

    print(f"## {title}")
    print()
    print("| Counter | Covered | Total | Coverage |")
    print("|---|---:|---:|---:|")
    for kind in COUNTERS:
        counter = totals.get(kind)
        if counter is None:
            continue
        covered, missed = int(counter.get("covered", "0")), int(counter.get("missed", "0"))
        print(f"| {kind.lower()} | {covered} | {covered + missed} | {ratio(covered, missed)} |")
    print()
    print("| Module | Lines | Branches |")
    print("|---|---:|---:|")
    for module in sorted(modules):
        lines, branches = modules[module]["LINE"], modules[module]["BRANCH"]
        print(f"| `{module}` | {ratio(*lines)} | {ratio(*branches)} |")
    print()


def main(argv: list[str]) -> int:
    if len(argv) != 3 or argv[0] not in ("junit", "jacoco"):
        print(__doc__, file=sys.stderr)
        return 2
    kind, title, path = argv
    target = Path(path)
    if not target.exists():
        print(f"## {title}\n\nNo report found at `{path}`.\n")
        return 0
    if kind == "junit":
        junit(title, target)
    else:
        jacoco(title, target)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
