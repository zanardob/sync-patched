# sync-patched

A ReVanced patch bundle with Sync for Reddit patches. The bundle is separate from
the official ReVanced bundle and from TheLastWallaby's `revanced-redditsync`
bundle. All three bundles can load at the same time.

## Patches

| Patch | What it does |
|---|---|
| Fix swipe back transparency | Sync converts activities to and from translucent with hidden `Activity` methods. Android 11 added the public `Activity.setTranslucent(boolean)`. The hidden calls now fail without an error. The screen under the gesture stays opaque, and a swipe back from a post shows black until the transition ends. The patch replaces both helpers with the public API on API 30 and later. Sync's original code path stays below API 30 and as a fallback. |
| Select and copy post text | Comments open Comment actions and then Select text or Copy text. Posts have no equivalent. The post options sheet declares its rows in XML and offers only title, permalink, link, and username in its Copy dialog. The patch adds a Select text row above Copy. The row opens the selection dialog that the comment sheet uses. The patch also adds Copy text to the Copy dialog on every post. Select text and Copy text write the title, then the body. When a post has no body, they write the link instead. |

## Download

The latest bundle is on the [releases page](https://github.com/zanardob/sync-patched/releases).

## Requirements

- JDK 17 or newer. The build targets JVM 17. A newer JDK, such as 25, also works.
- A classic GitHub personal access token with the `read:packages` scope. The
  `app.revanced.patches` Gradle plugin and `app.revanced:patcher` come from
  GitHub Packages. GitHub Packages does not serve artifacts without a token, and
  it does not accept fine-grained tokens.

Put the token in `~/.gradle/gradle.properties`. Both repositories are named
`githubPackages`, so one pair of properties covers the plugin and its
dependencies:

```properties
githubPackagesUsername=your-github-username
githubPackagesPassword=ghp_yourtoken
```

## Build

```sh
./gradlew buildAndroid
```

Output: `patches/build/libs/patches-0.1.0.rvp`. The file name comes from `version`
in `gradle.properties`. A release build uses the tag version. Tag `v0.1.1`
produces `patches-0.1.1.rvp` and publishes it as `patches.rvp`. The bundle carries
both JVM classes and `classes.dex`, so ReVanced CLI and ReVanced Manager can load
it.

`buildAndroid` skips the ABI validation task that `build` runs. This bundle does
not keep an API dump.

## Use with ReVanced CLI

ReVanced CLI accepts more than one bundle per run. Bundle order does not matter.

```sh
java -jar revanced-cli.jar patch \
  -p revanced-patches.rvp -b \
  -p thelastwallaby.rvp -b \
  -p patches-0.1.0.rvp -b \
  -o sync-patched.apk \
  Sync.apk
```

`-b` bypasses the signature check and the build provenance check of the CLI. A
GitHub Release carries neither signature nor provenance, so `-b` stays required
for this bundle.

## Use with ReVanced Manager

Add this feed as a remote patch source. Manager then follows new releases:

```
https://raw.githubusercontent.com/zanardob/sync-patched/main/bundles/stable.json
```

1. Open Patches.
2. Tap the pencil icon.
3. Choose Add patches.
4. Choose Enter URL.
5. Paste the URL.
6. Keep auto-update on.

The feed points at the `patches.rvp` of the newest release.

Another option is to download `patches.rvp` from Releases and add it as a local
source. Manager keeps one patch list per source and combines the lists at patch
time.
