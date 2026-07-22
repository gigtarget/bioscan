# BioScan Kiosk

The complete Android source bundle has been uploaded to this repository. A one-time GitHub workflow will unpack it into the standard Android Studio project structure, generate the Gradle wrapper, commit the source to `main`, and attempt a debug APK build.

## Complete the publication

1. Open **Settings → Actions → General** in this repository.
2. Under **Actions permissions**, select **Allow all actions and reusable workflows**, then save.
3. Open the **Actions** tab.
4. Select **Bootstrap BioScan Source**.
5. Click **Run workflow**, choose `main`, and run it.

After the workflow finishes, refresh the repository. You should see `app/`, `gradle/`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`, and the other Android project files.

## Open in Android Studio

Use **File → New → Project from Version Control**, paste the repository address, and select the repository root. Android Studio should detect `settings.gradle.kts` and begin Gradle sync.

## APK

The workflow also attempts to build `app-debug.apk`. Open the completed workflow run and download the **BioScan-debug-apk** artifact.

## Development status

This is still a development prototype. The current face embedding implementation is not a production biometric model, so it should not be used for security-critical or payroll decisions until the recognition, liveness, backup, and security issues are properly repaired and tested on real devices.
