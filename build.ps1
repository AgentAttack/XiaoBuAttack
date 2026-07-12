# ============================================================
# 小布助手攻击App - 构建脚本
# 输出: xiaobu_attack.apk
# ============================================================
$ErrorActionPreference = "Stop"

$SDK = "$env:LOCALAPPDATA\Android\Sdk"
$BUILD_TOOLS = "$SDK\build-tools\37.0.0"
$PLATFORM = "$SDK\platforms\android-36.1\android.jar"
$JAVA_HOME = "C:\Program Files\Java\jdk-21"

# Paths
$AAPT = "$BUILD_TOOLS\aapt.exe"
$D8 = "$BUILD_TOOLS\d8.bat"
$APKSIGNER = "$BUILD_TOOLS\apksigner.bat"
$JAVAC = "$JAVA_HOME\bin\javac.exe"
$KEYTOOL = "$JAVA_HOME\bin\keytool.exe"

$PROJECT = Split-Path -Parent $MyInvocation.MyCommand.Path
$GEN = "$PROJECT\gen"
$OBJ = "$PROJECT\obj"
$KEYSTORE = "$PROJECT\debug.keystore"
$APK_UNSIGNED = "$PROJECT\xiaobu_attack_unsigned.apk"
$APK_SIGNED = "$PROJECT\xiaobu_attack.apk"

Write-Host "╔══════════════════════════════════════╗" -ForegroundColor Red
Write-Host "║  小布助手攻击App - 构建脚本          ║" -ForegroundColor Red
Write-Host "║  Target: Android 15 (API 36)         ║" -ForegroundColor Red
Write-Host "╚══════════════════════════════════════╝" -ForegroundColor Red
Write-Host ""

# Clean
Remove-Item -Recurse -Force $GEN, $OBJ -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $GEN, $OBJ | Out-Null

# Step 1: Generate keystore
if (-not (Test-Path $KEYSTORE)) {
    Write-Host "[1/6] 生成签名密钥..." -ForegroundColor Yellow
    & $KEYTOOL -genkey -v -keystore $KEYSTORE -alias attackkey `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=System, OU=Android, O=Unknown, L=Unknown, ST=Unknown, C=US" `
        -storepass android -keypass android 2>&1 | Out-Null
    Write-Host "  ✓ 密钥已生成" -ForegroundColor Green
} else {
    Write-Host "[1/6] 使用已有密钥" -ForegroundColor Yellow
}

# Step 2: Compile resources and generate R.java
Write-Host "[2/6] 编译资源和生成R.java..." -ForegroundColor Yellow
& $AAPT package -f -m `
    -M "$PROJECT\AndroidManifest.xml" `
    -S "$PROJECT\res" `
    -I $PLATFORM `
    -J $GEN `
    -F $APK_UNSIGNED `
    --auto-add-overlay 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ aapt失败! 检查路径和AndroidManifest.xml" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ R.java已生成" -ForegroundColor Green

# Step 3: Compile Java sources
Write-Host "[3/6] 编译Java源码..." -ForegroundColor Yellow
$srcFiles = Get-ChildItem -Recurse "$PROJECT\src\*.java" | ForEach-Object { $_.FullName }
$genFiles = Get-ChildItem -Recurse "$GEN\*.java" | ForEach-Object { $_.FullName }
$allFiles = @($srcFiles) + @($genFiles)

& $JAVAC -source 11 -target 11 `
    -cp $PLATFORM `
    -d $OBJ `
    -Xlint:none `
    $allFiles 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ javac失败!" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Java编译完成" -ForegroundColor Green

# Step 4: Convert to DEX
Write-Host "[4/6] 转换为DEX..." -ForegroundColor Yellow
& cmd /c "$D8 --lib $PLATFORM --output $OBJ\classes.dex $OBJ\com\attacker\xiaobu\*.class $OBJ\com\attacker\xiaobu\R*.class" 2>&1
if ($LASTEXITCODE -ne 0) {
    # 尝试找R.class
    $rClass = Get-ChildItem -Recurse "$OBJ\*.class" | Where-Object { $_.Name -like "R.class" -or $_.Name -like "R$*.class" }
    Write-Host "  R.class files: $($rClass -join ', ')"
    & cmd /c "$D8 --lib $PLATFORM --output $OBJ\classes.dex $(Get-ChildItem -Recurse $OBJ -Filter '*.class' | ForEach-Object { $_.FullName })" 2>&1
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ d8失败!" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ DEX生成完成" -ForegroundColor Green

# Step 5: Add DEX to APK
Write-Host "[5/6] 打包APK..." -ForegroundColor Yellow
Push-Location $OBJ
# Remove old META-INF and add dex
& cmd /c "zip -q -d `"$APK_UNSIGNED`" META-INF/\* 2>&1" | Out-Null
& cmd /c "zip -q -j `"$APK_UNSIGNED`" classes.dex 2>&1"
Pop-Location
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ zip失败!" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ APK打包完成" -ForegroundColor Green

# Step 6: Sign APK
Write-Host "[6/6] 签名APK..." -ForegroundColor Yellow
& cmd /c "$APKSIGNER sign --ks $KEYSTORE --ks-pass pass:android --key-pass pass:android --out $APK_SIGNED $APK_UNSIGNED 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ 签名失败!" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ 签名完成" -ForegroundColor Green

# Cleanup
Remove-Item $APK_UNSIGNED -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "╔══════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║  ✅ 构建成功!                         ║" -ForegroundColor Green
Write-Host "║  APK: xiaobu_attack.apk              ║" -ForegroundColor Green
Write-Host "║  权限: 0个                            ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════╝" -ForegroundColor Green

# Show APK info
$apkSize = (Get-Item $APK_SIGNED).Length / 1KB
Write-Host "  Size: $([math]::Round($apkSize, 1)) KB" -ForegroundColor Cyan
