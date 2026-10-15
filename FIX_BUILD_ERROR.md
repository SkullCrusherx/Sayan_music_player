# GitHub build fix (2026-10-15)

Your `.github/workflows/main.yml` is a valid GitHub Actions filename.

The error in the supplied build log is `cannot find symbol: androidx.annotation.Nullable` at `PlaybackService.java`.

## Correction

Added to `app/build.gradle` under `dependencies`:

```gradle
implementation 'androidx.annotation:annotation:1.8.2'
```

This supplies `@Nullable` used by the background media service. No workflow filename change is required beyond keeping one workflow in `.github/workflows/main.yml`.

## GitHub upload

Extract this archive. Upload the files and folders inside the extracted directory to your GitHub repository root, overwriting existing files. Delete the old `.github/workflows/build-apk.yml` if it remains in your repository, to avoid duplicate workflows.

Then run **Actions > Build Sayan Music & Movies APK > Run workflow**. Once the build and artifact upload succeed, download `Sayan-Music-Movies-Installable-Debug-APK` from the run Summary.

This corrects the specific two Java compilation errors in your log. A full APK build has not been run in this environment.
