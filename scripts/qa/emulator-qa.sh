#!/usr/bin/env bash
# Drives Classy Calc on a running emulator with adb: opens the Integral and Derivative modes, solves
# examples and typed problems, changes a keypad setting, and records screenshots, UI dumps and logs.
# Usage: emulator-qa.sh <apk> <output dir>. Exits 1 if any step failed or the app crashed.
set -u
APK=$1
OUT=$2
PKG=io.github.l0gic0l.classycalc
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
FAILS=0

log() { echo "[qa] $*" | tee -a "$OUT/steps.txt"; }
fail() { log "FAIL: $*"; FAILS=$((FAILS + 1)); }
# A failed dump (common mid-animation) still exits 0 and leaves the last file behind, so both
# copies are removed first and success is read from what uiautomator prints.
dump() {
  rm -f "$OUT/ui.xml"
  adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1
  adb shell uiautomator dump /sdcard/ui.xml 2>&1 | grep -q "dumped to" && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
}
find_node() { python3 "$HERE/ui.py" find "$OUT/ui.xml" "$@"; }

shot() {
  sleep 1
  adb exec-out screencap -p >"$OUT/$1.png"
  for _ in 1 2 3; do dump && break; sleep 1; done
  if [ -f "$OUT/ui.xml" ]; then
    cp "$OUT/ui.xml" "$OUT/$1.xml"
    python3 "$HERE/ui.py" summary "$OUT/$1.xml" >"$OUT/$1.txt"
  else
    fail "no UI dump for $1"
  fi
  log "screenshot $1.png"
}

# Taps the node whose text or description is $1 (pass --contains to match part of it).
tap() {
  for _ in $(seq 1 20); do
    dump
    if xy=$(find_node "$@"); then
      adb shell input tap $xy
      log "tap '$1' at $xy"
      sleep 1
      return 0
    fi
    sleep 0.5
  done
  fail "'$1' not found to tap"
  return 1
}

# Waits up to 30 s for $1 to be on screen.
expect() {
  for _ in $(seq 1 30); do
    dump
    if find_node "$@" >/dev/null; then
      log "ok: '$1' is shown"
      return 0
    fi
    sleep 1
  done
  fail "'$1' never appeared"
  return 1
}

SIZE=$(adb shell wm size | tail -n 1 | sed 's/.*: //' | tr -d '\r')
W=${SIZE%x*}
H=${SIZE#*x}
log "screen ${W}x${H}"
scroll_down() { adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 400; sleep 1; }
scroll_up() { adb shell input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 400; sleep 1; }
back() { adb shell input keyevent 4; sleep 1.5; }

adb logcat -c
adb install -r "$APK" >"$OUT/install.txt" 2>&1
cat "$OUT/install.txt" >>"$OUT/steps.txt"
if ! grep -q Success "$OUT/install.txt"; then fail "install failed"; exit 1; fi
# Fresh settings and inputs, so every toggle starts from its default on a reused emulator too.
adb shell pm clear "$PKG" >/dev/null
ACTIVITY=$(adb shell cmd package resolve-activity --brief "$PKG" | tail -n 1 | tr -d '\r')
log "launch $ACTIVITY"
adb shell am start -W -n "$ACTIVITY" >/dev/null
expect "Classy Calc" && shot 01-menu

# Integral: a worked example, then the steps below it.
tap "Integral"
expect "Integrate" --contains && shot 02-integral-empty
tap "Examples" && shot 03-integral-examples
tap "Xe^X"
expect "Result" && shot 04-integral-result
scroll_down && shot 05-integral-steps
scroll_down && shot 06-integral-steps-more

# Type sin(X)² on the keypad and solve it with EXE.
scroll_up; scroll_up; scroll_up
tap "Integrate:" --contains
expect "Execute" && shot 07-integral-editor
tap "AC"; tap "sin"; tap "X,θ,T"; tap ")"; tap "x²"
shot 08-integral-typed
tap "Execute"
expect "sin(2X)" --contains && shot 09-integral-typed-result

# Definite: the example fills in 0 and 1 as limits.
scroll_up; scroll_up
tap "Definite"
tap "Examples"; tap "Xe^X"
expect "numerical integration agrees" --contains && shot 10-integral-definite

# A pole at the lower limit: ∫ from 0 to 1 of 1/X² has no value, and the card must say so.
tap "Integrate:" --contains
tap "AC"; tap "1"; tap "÷"; tap "X,θ,T"; tap "x²"
tap "Execute"
expect "does not converge" --contains && shot 10b-integral-diverges
back

# Derivative: an example with a value at a point, then the second derivative.
expect "Classy Calc"
tap "Derivative"
expect "Differentiate" --contains
tap "Value at a point"
tap "Examples"; tap "X^2sin(X)"
# f′(1) = cos(1) + 2sin(1); f″(1) = sin(1) + 4cos(1).
expect "2sin(1)" --contains && shot 11-derivative
tap "d²/dx²"
expect "4cos(1)" --contains && shot 12-second-derivative
back

# Settings: pill-shaped keys, then the Run-Matrix keypad.
expect "Classy Calc"
tap "Settings"
# Scrolls down until $1 is on screen.
scroll_to() {
  for _ in 1 2 3 4 5 6; do
    dump
    find_node "$@" >/dev/null && return 0
    scroll_down
  done
}
scroll_to "Answer sparkles" --contains
tap "Answer sparkles" --contains && shot 13a-settings-sparkles-off
scroll_to "Pill"
tap "Pill" && shot 13-settings-keys
back

# Run-Matrix: the live preview shows the answer before EXE.
tap "Run-Matrix"
tap "1"; tap "+"; tap "2"
expect "Preview =3" --contains && shot 14-run-preview
tap "Execute"
shot 15-run-pill-keys

# Evidence: logs, crashes and whether the app is still running.
adb logcat -d >"$OUT/logcat.txt"
adb logcat -b crash -d >"$OUT/crash.txt"
PID=$(adb shell pidof -s "$PKG" | tr -d '\r')
if [ -n "$PID" ]; then
  adb logcat -d --pid "$PID" >"$OUT/logcat-app.txt"
  log "app still running (pid $PID)"
else
  fail "app is not running at the end"
fi
if grep -q "$PKG" "$OUT/crash.txt"; then fail "crash in the crash buffer"; fi
log "$FAILS failure(s)"
[ "$FAILS" -eq 0 ]
