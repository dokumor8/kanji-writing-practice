"""Prints the println output and failure messages of a Gradle unit-test XML report."""
import html
import re
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
tree = ET.parse(path)
root = tree.getroot()
print("tests={tests} failures={failures} errors={errors}".format(**root.attrib))
for case in root.iter("testcase"):
    failures = case.findall("failure")
    status = "FAIL" if failures else "ok  "
    print("[{}] {}".format(status, case.get("name")))
    for message in [f.get("message") or "" for f in failures]:
        print("      ->", html.unescape(message).strip().splitlines()[0][:180])
for out in root.iter("system-out"):
    for line in (out.text or "").splitlines():
        if line.strip():
            print("   ", line.strip())
