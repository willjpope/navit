apt-get update && apt-get install -y software-properties-common
add-apt-repository -y ppa:openjdk-r/ppa
apt-get update && apt-get install -y openjdk-8-jdk wget expect git libsaxonb-java ant unzip libc6-dev
apt-get remove -y openjdk-7-jre-headless

export ANDROID_HOME=/opt/android-sdk-linux
export ANDROID_SDK_HOME=/opt/android-sdk-linux
export ANDROID_SDK_ROOT=/opt/android-sdk-linux

cd /opt
wget -q https://dl.google.com/android/repository/sdk-tools-linux-3859397.zip -O android-sdk.zip
unzip android-sdk.zip -d ${ANDROID_HOME}/ && rm -f android-sdk.zip

export PATH=${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/tools:${PATH}

mkdir -p ${ANDROID_HOME}/.android
touch ${ANDROID_HOME}/.android/repositories.cfg
echo "y" | sdkmanager --update
echo "y" | sdkmanager "platform-tools"

for ANDROID_VERSION in 25 24 23 18 16
do
  echo "y" | sdkmanager "system-images;android-${ANDROID_VERSION};google_apis;armeabi-v7a"
  echo "y" | sdkmanager "system-images;android-${ANDROID_VERSION};google_apis;x86"
done

for ANDROID_VERSION in 25.0.3 25.0.2 25.0.1 25.0.0 24.0.3 24.0.2 24.0.1 23.0.3 23.0.2 23.0.1
do
  echo "y" | sdkmanager "build-tools;${ANDROID_VERSION}"
done
