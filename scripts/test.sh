#!/usr/bin/env bash
# JVM unit tests (no device needed).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
./gradlew :app:testDebugUnitTest
