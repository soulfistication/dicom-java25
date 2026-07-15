#!/bin/sh
#
# build.sh
# Compiles the DICOM viewer for JDK 25 and packages it into dist/DicomViewer.jar.
#
set -e
cd "$(dirname "$0")"

SRC_DIR=src
BUILD_DIR=build/classes
DIST_DIR=dist
JAR="$DIST_DIR/DicomViewer.jar"
RELEASE=25

rm -rf build dist
mkdir -p "$BUILD_DIR" "$DIST_DIR"

SOURCES=$(find "$SRC_DIR" -name '*.java')

probe_dir=$(mktemp -d)
trap 'rm -rf "$probe_dir"' EXIT
echo 'class Probe {}' > "$probe_dir/Probe.java"
if ! javac --release "$RELEASE" -d "$probe_dir" "$probe_dir/Probe.java" >/dev/null 2>&1; then
    echo "ERROR: JDK $RELEASE or newer is required (javac --release $RELEASE)."
    echo "       Current javac: $(javac -version 2>&1)"
    exit 1
fi

echo "Compiling (javac --release $RELEASE)..."
javac --release "$RELEASE" -d "$BUILD_DIR" $SOURCES

echo "Main-Class: dicomviewer.Main" > build/manifest.txt
jar cfm "$JAR" build/manifest.txt -C "$BUILD_DIR" .

echo "Built $JAR"
