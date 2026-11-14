# Public development signing key

This intentionally public keystore signs DEVELOPMENT APKs only. It is not a production credential or proof of publisher identity.

- alias: `phoenix-development`
- store/key password: `android`
- Keep this file stable for development APK updates. Never regenerate it casually.
- 0.2 used the disposable CI debug key. Installing 0.3 over that build may require removing 0.2 first; local app data will be lost on uninstall.
- Use a separate private signing key for production distribution.
