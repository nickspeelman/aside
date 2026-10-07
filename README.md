# Aside

Aside is a local-first Android mood journal designed to demand as little attention as possible. It is currently **alpha software**.

The project is built around a few deliberately checkable ideas: no Aside account, no ads, no analytics/tracking, no subscriptions or in-app purchases, portable user data, and no ordinary Internet permission in release builds.

## Privacy and trust

- **Privacy policy:** [`PRIVACY.md`](PRIVACY.md) — version-controlled with the app so every wording change can be reviewed.
- **Commitments:** [`COMMITMENTS.md`](COMMITMENTS.md) — explains Aside's product commitments, including open code and inspectability, and maps them to the code or process that supports them, including important limits.
- **Security reports:** [`SECURITY.md`](SECURITY.md) — vulnerabilities can be reported privately to `asidehelp@nickspeelman.com`.
- **Branding/trademark note:** [`TRADEMARKS.md`](TRADEMARKS.md).
- **Changelog:** [`CHANGELOG.md`](CHANGELOG.md) — privacy/data-safety fixes are called out explicitly.

The repository includes a GitHub Pages workflow that publishes the tracked `PRIVACY.md` file. Once Pages is enabled for the repository using **GitHub Actions**, it is configured to publish at:

`https://nickspeelman.github.io/aside/privacy/`

The app's Privacy Policy link points to that page.

## No-network release invariant

Aside release builds are required to omit `android.permission.INTERNET`.

The Android Gradle build checks the **final merged release manifest**—including permissions contributed by dependencies—before that manifest is packaged into a release APK or AAB. If `android.permission.INTERNET` appears, the build fails.

Run the check directly with:

```bash
./gradlew :app:verifyReleaseNoInternetPermission
```

This is a build guard against accidentally weakening Aside's no-network design. `.github/workflows/android-ci.yml` runs the unit tests and this manifest check on pushes/pull requests. A third party can separately inspect the permissions in an installed APK to verify what that binary actually requests.

## Data portability

Aside supports human-readable CSV export and full-fidelity JSON backup/restore. These formats are intentionally portable rather than tied to a server or account. They are also plaintext/readable files, so users should treat saved/shared copies as sensitive journal data.

## License

Aside is free software licensed under the **GNU General Public License, version 3 (GPLv3)**. See [`LICENSE`](LICENSE).

GPLv3 permits use, modification, redistribution, and commercial distribution under its terms. Trademark/source-identifying branding is separate; see [`TRADEMARKS.md`](TRADEMARKS.md).
