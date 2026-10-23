#!/bin/bash
source "$(dirname "$0")/common.sh"
exec ./mvnw spring-boot:run
