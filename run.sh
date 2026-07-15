#!/bin/sh
#
# run.sh
# Builds (if needed) and launches the DICOM viewer.
#
set -e
cd "$(dirname "$0")"

if [ ! -f dist/DicomViewer.jar ]; then
    ./build.sh
fi

exec java -jar dist/DicomViewer.jar "$@"
