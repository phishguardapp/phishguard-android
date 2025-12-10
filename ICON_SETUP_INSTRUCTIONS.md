# Icon Setup Instructions

## Step 1: Save Your Icon Image

1. **Right-click** on the icon image you provided
2. **Save As** → `ic_phishguard_logo.png`
3. **Copy** the file to: `app/src/main/res/drawable/ic_phishguard_logo.png`

## Step 2: Code Changes Applied

The following files have been updated to use your custom icon instead of the emoji:

### MainActivity.kt
- Main screen icon (replaced 🛡️ with Image composable)
- VPN permission dialog icon (replaced 🛡️ with Image composable)

### OnboardingActivity.kt  
- Onboarding screen icon (replaced 🛡️ with Image composable)

## Step 3: Test the Changes

After placing the icon file:

1. **Build the app**: `./gradlew assembleDebug`
2. **Install**: `./gradlew installDebug`
3. **Verify**: Your custom icon should appear in all screens

## Icon Requirements

- **File name**: `ic_phishguard_logo.png`
- **Location**: `app/src/main/res/drawable/`
- **Recommended size**: 512x512px (will be scaled automatically)
- **Format**: PNG with transparency
- **Background**: Transparent (the icon will adapt to different themes)

## If Icon Doesn't Appear

1. **Check file location**: Ensure it's in the correct drawable folder
2. **Check file name**: Must be exactly `ic_phishguard_logo.png`
3. **Clean build**: Run `./gradlew clean` then `./gradlew assembleDebug`
4. **Restart Android Studio**: Sometimes needed for new resources

## Next Steps

Once the icon is working:
1. ✅ Custom icon in app UI
2. ⏳ Create launcher icons (all mipmap sizes)
3. ⏳ Take screenshots
4. ⏳ Create feature graphic
5. ⏳ Submit to Play Store