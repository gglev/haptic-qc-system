#!/bin/sh
set -eu
cd "$(dirname "$0")"
QC_JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then
    QC_JAVA="$JAVA_HOME/bin/java"
fi
if [ ! -f build/classes/qc/app/Main.class ]; then
    sh build.sh
fi
exec "$QC_JAVA" -cp 'build/classes:lib/gson-2.15.0.jar' qc.app.Main "$@"
