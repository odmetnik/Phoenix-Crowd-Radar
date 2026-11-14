#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_output=$(mktemp -d)
trap 'rm -rf "$test_output"' EXIT
if command -v javac >/dev/null 2>&1; then
  javac -encoding UTF-8 -d "$test_output" app/src/main/java/hr/phoenixart/crowdradar/core/*.java tests/CoreTest.java tests/SyntaxCheck.java
else
  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$test_output" app/src/main/java/hr/phoenixart/crowdradar/core/*.java tests/CoreTest.java tests/SyntaxCheck.java
fi
java -cp "$test_output" CoreTest
java -cp "$test_output" SyntaxCheck
