# Save Your Professional Icon

## Quick Steps:

1. **Right-click** on the icon image you just shared
2. **Save As** → `ic_phishguard_logo.png`
3. **Save to**: `app/src/main/res/drawable/ic_phishguard_logo.png`

## Exact File Path:
```
PhishGuard/
└── app/
    └── src/
        └── main/
            └── res/
                └── drawable/
                    └── ic_phishguard_logo.png  ← Save here
```

## After Saving:

1. **Build**: `./gradlew assembleDebug`
2. **Install**: `./gradlew installDebug`
3. **Check**: Your beautiful icon should appear in the app!

## What You'll See:

- **Main Screen**: Large professional icon (120dp)
- **VPN Dialog**: Smaller version (48dp) 
- **Onboarding**: Large icon on welcome screen (120dp)

The icon will automatically scale and look crisp on all screen densities.

## If It Doesn't Work:

1. Check file name is exactly: `ic_phishguard_logo.png`
2. Check location: `app/src/main/res/drawable/`
3. Clean build: `./gradlew clean assembleDebug`
4. Restart Android Studio if needed

Your icon design is perfect - modern, professional, and clearly communicates security/protection! 🎯