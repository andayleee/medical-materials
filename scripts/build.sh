#!/bin/bash
source "$(dirname "$0")/common.sh"
exec ./mvnw clean package
