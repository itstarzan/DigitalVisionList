# Digital Vision List

Android app for creating, saving, exporting and sharing shop lists.

## GitHub Actions signing

The workflow in `.github/workflows/build-signed-apk.yml` builds the release APK and signs it using protected GitHub Actions secrets.

Required repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never commit the `.jks` signing key to this repository.

## Current app version

- Version name: 1.7
- Version code: 8
- Application ID: `com.digitalvision.listapp`
