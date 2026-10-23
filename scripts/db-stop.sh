#!/bin/bash
source "$(dirname "$0")/common.sh"
"$PG_BIN/pg_ctl" -D .local/postgres -m fast -w stop
