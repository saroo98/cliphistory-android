#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/core-tests
kotlinc app/src/main/java/app/cliphistory/core/*.kt app/src/test/java/app/cliphistory/CoreSuite.kt -jvm-target 17 -include-runtime -d build/core-tests/tests.jar
java -cp build/core-tests/tests.jar app.cliphistory.CoreSuite
