#!/bin/bash
source "$(dirname "$0")/common.sh"
./mvnw -q -DskipTests package
exec "$JAVA_HOME/bin/java" -jar target/medical-materials-0.0.1-SNAPSHOT.jar --spring.profiles.active=bootstrap-admin --spring.main.web-application-type=none
