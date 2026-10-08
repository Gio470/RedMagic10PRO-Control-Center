#!/usr/bin/env bash
set -u

gpu_test_exit=0
./gradlew :app:connectedDebugAndroidTest || gpu_test_exit=$?
mkdir -p gpu-screenshots
adb pull /sdcard/Android/data/com.redmagic.control/files/card-blur-gpu/. gpu-screenshots/ || true
exit "$gpu_test_exit"
