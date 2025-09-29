#!/usr/bin/bash

docker run --rm --platform=linux/arm64/v8 -it "$@" godotengine/alpine-android:android-35-jdk17 /bin/bash

