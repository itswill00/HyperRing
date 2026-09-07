#!/system/bin/sh
set -e

PROJECT_DIR="/data/data/com.termux/files/home/HyperRing_Module"
OUTPUT_DIR="${PROJECT_DIR}/releases"
DOWNLOAD_DIR="/sdcard/Download"
MOD_TARGET="/data/adb/modules/hyperring"

cd "$PROJECT_DIR"

if [ ! -f "module.prop" ]; then
    echo "Error: module.prop not found"
    exit 1
fi

VERSION=$(grep '^version=' module.prop | cut -d= -f2)
VERSION_CODE=$(grep '^versionCode=' module.prop | cut -d= -f2)
ZIP_NAME="HyperRing-${VERSION}.zip"

MODE="build"
for arg in "$@"; do
    case "$arg" in
        --deploy|-d|deploy)
            MODE="build_and_deploy"
            ;;
        --deploy-only|deploy-only)
            MODE="deploy_only"
            ;;
        --build|-b|build)
            MODE="build"
            ;;
        --clean|-c|clean)
            rm -rf build/ bin/classes/ releases/ webui/dist/
            echo "Cleaned build artifacts"
            exit 0
            ;;
        --help|-h|help)
            echo "Usage: ./build.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  (no args) or build  Build WebUI, compile DEX, and package flashable zip"
            echo "  -d, --deploy, deploy Build WebUI, compile DEX, and deploy live to device"
            echo "  --deploy-only       Deploy existing compiled files directly to device"
            echo "  -c, --clean, clean  Clean build artifacts and cache"
            echo "  -h, --help, help    Show this help text"
            exit 0
            ;;
    esac
done

echo "HyperRing ${VERSION} (${VERSION_CODE})"

if [ "$MODE" != "deploy_only" ]; then
    # 1. Compile WebUI
    if [ -d "webui" ]; then
        echo "Building WebUI bundle..."
        (cd webui && node ./node_modules/vite/bin/vite.js build >/dev/null 2>&1)
        mkdir -p webroot
        cp webui/dist/index.html webroot/index.html
    fi

    # 2. Compile Java DEX
    ANDROID_JAR="/data/data/com.termux/files/usr/share/java/android.jar"
    if [ ! -f "$ANDROID_JAR" ]; then
        ANDROID_JAR=$(find /data/data/com.termux/files/ -name "android.jar" 2>/dev/null | head -n 1)
    fi

    if [ -z "$ANDROID_JAR" ] || [ ! -f "$ANDROID_JAR" ]; then
        echo "Error: android.jar not found"
        exit 1
    fi

    echo "Compiling Java DEX overlay..."
    mkdir -p build/classes bin
    rm -rf build/classes/*
    ecj -cp "$ANDROID_JAR" -d build/classes src/HyperRingOverlay.java
    dx --dex --output=bin/hyperring.dex build/classes
    chmod 644 bin/hyperring.dex
    rm -rf build/classes

    # 3. Package flashable ZIP
    mkdir -p "$OUTPUT_DIR"
    # Prune older release builds to conserve internal storage
    rm -f "$OUTPUT_DIR"/HyperRing-*.zip

    echo "Packaging release archive..."
    zip -r "$OUTPUT_DIR/$ZIP_NAME" \
        action.sh \
        bin/hyperring.dex \
        customize.sh \
        module.prop \
        service.sh \
        state/config.json \
        uninstall.sh \
        webroot/index.html \
        README.md >/dev/null 2>&1

    # Copy to user Download directory if available
    su -c "mkdir -p $DOWNLOAD_DIR && rm -f $DOWNLOAD_DIR/HyperRing-*.zip && cp $OUTPUT_DIR/$ZIP_NAME $DOWNLOAD_DIR/$ZIP_NAME && am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$DOWNLOAD_DIR/$ZIP_NAME' >/dev/null 2>&1" 2>/dev/null || true

    echo "Built: ${OUTPUT_DIR}/${ZIP_NAME}"
    echo "Exported: ${DOWNLOAD_DIR}/${ZIP_NAME}"
fi

# 4. Deploy logic
if [ "$MODE" = "build_and_deploy" ] || [ "$MODE" = "deploy_only" ]; then
    echo "Deploying to ${MOD_TARGET}..."
    if ! su -c "test -d /data/adb/modules"; then
        echo "Error: Root access or /data/adb/modules directory not found"
        exit 1
    fi

    su -c "
        mkdir -p $MOD_TARGET/bin $MOD_TARGET/webroot $MOD_TARGET/state
        cp $PROJECT_DIR/bin/hyperring.dex $MOD_TARGET/bin/hyperring.dex
        cp $PROJECT_DIR/webroot/index.html $MOD_TARGET/webroot/index.html
        cp $PROJECT_DIR/module.prop $MOD_TARGET/module.prop
        cp $PROJECT_DIR/action.sh $MOD_TARGET/action.sh
        cp $PROJECT_DIR/service.sh $MOD_TARGET/service.sh
        cp $PROJECT_DIR/customize.sh $MOD_TARGET/customize.sh
        cp $PROJECT_DIR/uninstall.sh $MOD_TARGET/uninstall.sh
        chmod 755 $MOD_TARGET/action.sh $MOD_TARGET/service.sh $MOD_TARGET/customize.sh $MOD_TARGET/uninstall.sh
        chmod 644 $MOD_TARGET/bin/hyperring.dex $MOD_TARGET/webroot/index.html $MOD_TARGET/module.prop
        chmod 777 $MOD_TARGET/state
        chcon -R u:object_r:system_file:s0 $MOD_TARGET
        sh $MOD_TARGET/action.sh
    "
    echo "Deployment completed successfully."
fi
