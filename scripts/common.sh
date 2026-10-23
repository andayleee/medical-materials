#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${VKR_JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/openjdk-21.0.1/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
PG_BIN="${VKR_PG_BIN:-/Library/PostgreSQL/18/bin}"
cd "$ROOT"
set -a
source "$ROOT/.env"
set +a
export MAVEN_USER_HOME="$ROOT/.local/maven-user"
