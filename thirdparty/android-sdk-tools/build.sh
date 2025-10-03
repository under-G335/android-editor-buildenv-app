#!/bin/bash

BUILD_TOOLS_VERSION="35.0.2"

if [ -z "$ANDROID_NDK_ROOT" ]; then
	echo "ERROR: The ANDROID_NDK_ROOT environment variable must be defined" > /dev/stderr
	exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$SCRIPT_DIR/android-sdk-tools-source"
BUILD_DIR="$SCRIPT_DIR/build"
OUTPUT_DIR="$SCRIPT_DIR/../../docker/android-sdk-tools"

function die() {
    echo "$@" > /dev/stderr
    exit 1
}

mkdir -p $BUILD_DIR
cd $BUILD_DIR

python $SOURCE_DIR/get_source.py --tags build-tools-$BUILD_TOOLS_VERSION

