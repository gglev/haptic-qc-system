#!/bin/sh
set -eu
cd "$(dirname "$0")"

QC_JAVAC=javac
if [ -n "${JAVA_HOME:-}" ]; then
    QC_JAVAC="$JAVA_HOME/bin/javac"
fi
QC_JAR=lib/gson-2.15.0.jar
QC_SHA=96d3dd843dc2911de01cdabcd50064cb9e860353102a0a78c96fd833d396fc7d
mkdir -p lib
if [ ! -f "$QC_JAR" ]; then
    QC_DOWNLOAD="$QC_JAR.download.$$"
    trap 'rm -f "$QC_DOWNLOAD"' EXIT HUP INT TERM
    curl -fL --connect-timeout 10 --max-time 60 --retry 2 -o "$QC_DOWNLOAD" \
        https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.15.0/gson-2.15.0.jar
    printf '%s  %s\n' "$QC_SHA" "$QC_DOWNLOAD" | shasum -a 256 -c -
    mv "$QC_DOWNLOAD" "$QC_JAR"
    trap - EXIT HUP INT TERM
fi
printf '%s  %s\n' "$QC_SHA" "$QC_JAR" | shasum -a 256 -c -
rm -rf build/classes
mkdir -p build/classes
"$QC_JAVAC" --release 21 -encoding UTF-8 -cp "$QC_JAR" \
    -d build/classes src/qc/model/*.java src/qc/analysis/*.java src/qc/app/*.java tests/qc/*.java
printf 'Сборка завершена\n'
