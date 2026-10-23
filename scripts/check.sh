#!/bin/bash
set -euo pipefail
curl --fail --silent --show-error http://127.0.0.1:8080/api/info
printf '\n'
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
printf '\n'
