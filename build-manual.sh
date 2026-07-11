#!/bin/bash
set -e

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT_DIR="/root/myproject/resolution-switcher"
SRC_DIR="$PROJECT_DIR/app/src/main"
RES_DIR="$SRC_DIR/res"
JAVA_SRC="$SRC_DIR/java"
MANIFEST="$SRC_DIR/AndroidManifest.xml"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"

BUILD_DIR="$PROJECT_DIR/build-manual"
CLASSES_DIR="$BUILD_DIR/classes"
RES_FLAT_DIR="$BUILD_DIR/res-flat"
COMPILED_RES_DIR="$BUILD_DIR/compiled-res"
OUTPUT_DIR="$PROJECT_DIR/app/build/outputs/apk/debug"

KOTLINC="/opt/kotlinc/bin/kotlinc"
D8="$ANDROID_HOME/build-tools/35.0.0/d8"
APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"

# AAPT2 binary (ARM64 native from system)
AAPT2="/usr/bin/aapt2"

echo "=== Step 1: Clean ==="
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR" "$CLASSES_DIR" "$RES_FLAT_DIR" "$COMPILED_RES_DIR" "$OUTPUT_DIR"

echo "=== Step 2: Download dependencies ==="
DEPS_DIR="$BUILD_DIR/deps"
mkdir -p "$DEPS_DIR"

# Download from Maven Central (Java-based libs)
for url in \
    "https://repo1.maven.org/maven2/dev/rikka/shizuku/api/13.1.5/api-13.1.5.aar" \
    "https://repo1.maven.org/maven2/dev/rikka/shizuku/provider/13.1.5/provider-13.1.5.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/core/core/1.12.0/core-1.12.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.12.0/core-ktx-1.12.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/appcompat/appcompat/1.6.1/appcompat-1.6.1.aar" \
    "https://dl.google.com/dl/android/maven2/com/google/android/material/material/1.11.0/material-1.11.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/1.7.1/annotation-1.7.1.jar" \
    "https://dl.google.com/dl/android/maven2/androidx/collection/collection/1.3.0/collection-1.3.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-common/2.7.0/lifecycle-common-2.7.0.jar" \
    "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime/2.7.0/lifecycle-runtime-2.7.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.8.2/activity-1.8.2.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/fragment/fragment/1.6.2/fragment-1.6.2.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/resourceinspection/resourceinspection-annotation/1.0.1/resourceinspection-annotation-1.0.1.jar" \
    "https://dl.google.com/dl/android/maven2/androidx/customview/customview/1.1.0/customview-1.1.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/interpolator/interpolator/1.0.0/interpolator-1.0.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/vectordrawable/vectordrawable/1.1.0/vectordrawable-1.1.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/vectordrawable/vectordrawable-animated/1.1.0/vectordrawable-animated-1.1.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/viewpager/viewpager/1.0.0/viewpager-1.0.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/drawerlayout/drawerlayout/1.1.1/drawerlayout-1.1.1.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/coordinatorlayout/coordinatorlayout/1.1.0/coordinatorlayout-1.1.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/concurrent/concurrent-futures/1.1.0/concurrent-futures-1.1.0.jar" \
    "https://dl.google.com/dl/android/maven2/androidx/tracing/tracing/1.2.0/tracing-1.2.0.aar" \
    "https://dl.google.com/dl/android/maven2/com/google/guava/listenablefuture/1.0/listenablefuture-1.0.jar" \
    "https://repo1.maven.org/maven2/org/checkerframework/checker-qual/3.3.0/checker-qual-3.3.0.jar" \
    "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-livedata/2.7.0/lifecycle-livedata-2.7.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-livedata-core/2.7.0/lifecycle-livedata-core-2.7.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel/2.7.0/lifecycle-viewmodel-2.7.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-savedstate/2.7.0/lifecycle-viewmodel-savedstate-2.7.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/savedstate/savedstate/1.2.1/savedstate-1.2.1.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/savedstate/savedstate/1.2.1/savedstate-1.2.1.jar" \
    "https://dl.google.com/dl/android/maven2/androidx/loader/loader/1.1.0/loader-1.1.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/startup/startup-runtime/1.1.1/startup-runtime-1.1.1.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/emoji2/emoji2/1.3.0/emoji2-1.3.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/emoji2/emoji2-views-helper/1.3.0/emoji2-views-helper-1.3.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/core/core/1.12.0/core-1.12.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/annotation/annotation-experimental/1.4.0/annotation-experimental-1.4.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/core/core/1.9.0/core-1.9.0.aar" \
    "https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/1.7.0/annotation-1.7.0.jar" \
    "https://dl.google.com/dl/android/maven2/com/google/errorprone/error_prone_annotations/2.18.0/error_prone_annotations-2.18.0.jar" \
; do
    fname=$(basename "$url")
    if [ ! -f "$DEPS_DIR/$fname" ]; then
        echo "  Downloading $fname..."
        curl -4 --max-time 30 -sL -o "$DEPS_DIR/$fname" "$url" 2>/dev/null || echo "  WARN: failed $fname"
    fi
done

echo "=== Step 3: Extract AARs and collect JARs ==="
JARS_DIR="$BUILD_DIR/jars"
mkdir -p "$JARS_DIR"

for aar in "$DEPS_DIR"/*.aar; do
    [ -f "$aar" ] || continue
    name=$(basename "$aar" .aar)
    mkdir -p "$BUILD_DIR/aar-$name"
    unzip -qo "$aar" -d "$BUILD_DIR/aar-$name" 2>/dev/null
    # Extract classes.jar from AAR
    if [ -f "$BUILD_DIR/aar-$name/classes.jar" ]; then
        cp "$BUILD_DIR/aar-$name/classes.jar" "$JARS_DIR/$name.jar"
    fi
done

for jar in "$DEPS_DIR"/*.jar; do
    [ -f "$jar" ] || continue
    cp "$jar" "$JARS_DIR/"
done

echo "  JARs found:"
ls "$JARS_DIR/" | head -20

echo "=== Step 4: Compile resources with aapt2 ==="
# Since aapt2 daemon mode doesn't work via QEMU, use non-daemon mode
# But first let's try the system aapt (v1) which should work with simple resources

# Actually, let's use aapt2 in non-daemon mode (single-shot)
# Create a minimal resources.arsc with the system aapt
echo "  Using aapt (v1) for resource linking..."

# First compile XML resources using aapt (v1 can handle this)
mkdir -p "$BUILD_DIR/intermediates/res"

# Use aapt to compile and link all resources into a flat file
/usr/bin/aapt package -f -m \
    -S "$RES_DIR" \
    -J "$BUILD_DIR/gen" \
    -M "$MANIFEST" \
    -I "$ANDROID_JAR" \
    --auto-add-overlay \
    -F "$BUILD_DIR/resources.ap_" 2>&1

echo "  Resource linking done."

echo "=== Step 5: Generate R.java ==="
# R.java should have been generated by aapt
find "$BUILD_DIR/gen" -name "R.java" -type f 2>/dev/null | head -5

echo "=== Step 6: Compile Kotlin ==="
# Build classpath from JARs + android.jar
CP="$ANDROID_JAR"
for jar in "$JARS_DIR"/*.jar; do
    CP="$CP:$jar"
done

# Also include generated R.java classpath
find "$BUILD_DIR/aar-" -name "classes.jar" -type f | while read j; do
    CP="$CP:$j"
done

echo "  Compiling Kotlin sources..."
"$KOTLINC" \
    -classpath "$CP" \
    -jvm-target 17 \
    -d "$CLASSES_DIR" \
    -nowarn \
    "$JAVA_SRC/com/resolution/switcher/util/PermissionHelper.kt" \
    "$JAVA_SRC/com/resolution/switcher/resolution/ResolutionController.kt" \
    "$JAVA_SRC/com/resolution/switcher/resolution/RootResolutionMethod.kt" \
    "$JAVA_SRC/com/resolution/switcher/resolution/ShizukuResolutionMethod.kt" \
    "$JAVA_SRC/com/resolution/switcher/presets/PresetStorage.kt" \
    "$JAVA_SRC/com/resolution/switcher/MainActivity.kt" \
    "$JAVA_SRC/com/resolution/switcher/OverlayService.kt" 2>&1

echo "  Kotlin compilation done."

echo "=== Step 7: Convert to DEX ==="
# Find all .class files
find "$CLASSES_DIR" -name "*.class" | wc -l

# Build a list of all class files and JARs for d8
echo "  Running d8..."
find "$CLASSES_DIR" -name "*.class" > "$BUILD_DIR/classfiles.txt"

# Create a temp file with all class paths
CLASSPATH_ARG=""
for jar in "$JARS_DIR"/*.jar; do
    CLASSPATH_ARG="$CLASSPATH_ARG --lib $jar"
done

"$D8" \
    --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output "$BUILD_DIR" \
    $(find "$CLASSES_DIR" -name "*.class") 2>&1

echo "  DEX conversion done."

echo "=== Step 8: Build APK ==="
# Combine resources + dex into APK
cp "$BUILD_DIR/resources.ap_" "$BUILD_DIR/app.unsigned.apk"

# Add DEX to APK
cd "$BUILD_DIR"
# Unzip the resources.ap_ and add classes.dex
mkdir -p apk-temp
cd apk-temp
unzip -qo "../resources.ap_" 2>/dev/null
cp "../classes.dex" .
zip -qr "../app.unsigned.apk" classes.dex
cd "$BUILD_DIR"
rm -rf apk-temp

echo "=== Step 9: Align APK ==="
# zipalign is x86_64, skip it for now (not strictly required for debug)

echo "=== Step 10: Sign APK ==="
mkdir -p "$OUTPUT_DIR"

# Generate debug keystore
if [ ! -f "$BUILD_DIR/debug.keystore" ]; then
    keytool -genkeypair -v \
        -keystore "$BUILD_DIR/debug.keystore" \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" 2>&1
fi

# Sign with apksigner
"$APKSIGNER" sign \
    --ks "$BUILD_DIR/debug.keystore" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUTPUT_DIR/app-debug.apk" \
    "$BUILD_DIR/app.unsigned.apk" 2>&1

echo ""
echo "=== BUILD COMPLETE ==="
echo "APK location: $OUTPUT_DIR/app-debug.apk"
ls -la "$OUTPUT_DIR/app-debug.apk"
