#!/usr/bin/env python3
"""Reads a uiautomator dump.

  ui.py find FILE TARGET [--contains]   centre "x y" of the first node whose text or
                                        content description is TARGET (or contains it)
  ui.py summary FILE                    one line per node with text or a description
"""
import re
import sys
import xml.etree.ElementTree as ET


def nodes(path):
    return ET.parse(path).getroot().iter("node")


def centre(bounds):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(path, target, contains):
    for n in nodes(path):
        for attr in ("text", "content-desc"):
            value = n.get(attr) or ""
            if value == target or (contains and target in value):
                print(*centre(n.get("bounds")))
                return 0
    return 1


def summary(path):
    for n in nodes(path):
        text = n.get("text") or ""
        desc = n.get("content-desc") or ""
        if text or desc:
            print(f"{n.get('bounds')} text={text!r} desc={desc!r} clickable={n.get('clickable')}")
    return 0


def main(argv):
    if len(argv) >= 3 and argv[0] == "find":
        return find(argv[1], argv[2], "--contains" in argv[3:])
    if len(argv) == 2 and argv[0] == "summary":
        return summary(argv[1])
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except (ET.ParseError, FileNotFoundError):
        sys.exit(1)
