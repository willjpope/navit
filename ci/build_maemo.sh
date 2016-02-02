#!/bin/bash
red='\e[0;31m'
grn='\e[0;32m'
yel='\e[1;33m'
off='\e[0m'

export START_PATH=~/
export SOURCE_PATH=$START_PATH"/"${CIRCLE_PROJECT_REPONAME}"/"
export BUILD_PATH=$START_PATH"/maemo"
export DFSG=navit-0.5.0+dfsg.1

mkdir -p $BUILD_PATH
cd $BUILD_PATH
mkdir $CIRCLE_ARTIFACTS/maemo/

cp -R $SOURCE_PATH $DFSG
cd $DFSG

cmake -D SRC=navit/version.h.in \
      -D DST=../version \
      -D NAME=GIT_VERSION \
      -D STRIP_M="1" \
      -P cmake/version.cmake

export NAVIT_VERSION=`grep GIT_VERSION ../version | grep -o -E '[0-9]+'`

cp -R contrib/maemo/debian .

dch -v 0.5.0+dfsg.1-1maemo1-$NAVIT_VERSION New upstream snapshot $NAVIT_VERSION || exit 1

dpkg-buildpackage -S -uc -us || exit 1

cp $BUILD_PATH/*.tar.gz $BUILD_PATH/*.dsc $BUILD_PATH/*.changes $CIRCLE_ARTIFACTS/maemo/ || exit 1

