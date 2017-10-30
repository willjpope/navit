#!/bin/bash
set -e

apt-get update && apt-get install -y wget

export ARCH="arm"
export START_PATH=~/
export SOURCE_PATH=$START_PATH"/"${CIRCLE_PROJECT_REPONAME}"/"
export CMAKE_FILE=$SOURCE_PATH"/Toolchain/arm-eabi.cmake"
export ANDROID_NDK=~/android-ndk-r11c
export ANDROID_NDK_BIN=$ANDROID_NDK"/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/bin"
export ANDROID_SDK=/opt/android-sdk-linux
export ANDROID_HOME=/opt/android-sdk-linux
export ANDROID_SDK_HOME=/opt/android-sdk-linux
export ANDROID_SDK_ROOT=/opt/android-sdk-linux
export ANDROID_SDK_PLATFORM_TOOLS=$ANDROID_SDK"/platform-tools"
export BUILD_PATH=android-${ARCH}

export PATH=${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/tools:${PATH}

wget -nv -c http://dl.google.com/android/repository/android-ndk-r11c-linux-x86_64.zip
[ -d ~/android-ndk-r11c ] || unzip -q -d ~ android-ndk-r11c-linux-x86_64.zip

[ -d $BUILD_PATH ] || mkdir -p $BUILD_PATH
pushd $BUILD_PATH

cmake -DCMAKE_TOOLCHAIN_FILE=../Toolchain/arm-eabi.cmake -DCACHE_SIZE='(20*1024*1024)' -DAVOID_FLOAT=1 -DSAMPLE_MAP=n -DBUILD_MAPTOOL=n -DANDROID_API_VERSION=25 -DANDROID_NDK_API_VERSION=19 ../

make -j $(nproc --all)

if [[ "${CIRCLE_BRANCH}" == "master" ]]; then
  make -j $(nproc --all) apkg-release && mv navit/android/bin/Navit-release-unsigned.apk navit/android/bin/navit-$CIRCLE_SHA1-${ARCH}-release-unsigned.apk || exit 1
else
  make -j $(nproc --all) apkg && mv navit/android/bin/Navit-debug.apk navit/android/bin/navit-$CIRCLE_SHA1-${ARCH}-debug.apk || exit 1
fi

echo
echo "Build leftovers :"
find .
