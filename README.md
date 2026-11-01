# sync-patched

A ReVanced patch bundle that holds only my Sync for Reddit patches. It is separate
from the official ReVanced bundle and from TheLastWallaby's
`revanced-redditsync` bundle, so all three can be loaded at the same time.

## Patches

| Patch | What it does |
|---|---|
| Fix swipe back transparency | Sync converts activities to and from translucent by reflecting into hidden `Activity` methods. Android 11 added `Activity.setTranslucent(boolean)` and the hidden calls now fail silently, so the screen under the gesture stays opaque and swiping back from a post shows black until the transition ends. The patch replaces both helpers with the public API on API 30+, and keeps Sync's original code path below API 30 and as a fallback. |
| Select and copy post text | Comments open Comment actions and then Select text or Copy text. Posts have no equivalent, because the post options sheet declares its rows in XML and only offers title, permalink, link and username in its Copy dialog. The patch adds a Select text row above Copy that opens the same selection dialog the comment sheet uses, and adds Copy text to the Copy dialog on every post. Copy text writes the rendered body, or the title and the link when a post has no body. |

The swipe patch is a port of `wchill/patcheddit#135` to the ReVanced patcher API.

## Requirements

- JDK 17 or newer. The build targets JVM 17, so a newer JDK such as 25 works too.
- A classic GitHub personal access token with the `read:packages` scope. The
  `app.revanced.patches` Gradle plugin and `app.revanced:patcher` are published to
  GitHub Packages, which does not serve artifacts anonymously and does not accept
  fine-grained tokens.

Put the token in `~/.gradle/gradle.properties`. Both repositories are named
`githubPackages`, so one pair covers the plugin and its dependencies:

```properties
githubPackagesUsername=your-github-username
githubPackagesPassword=ghp_yourtoken
```

## Build

```sh
./gradlew buildAndroid
```

Output: `patches/build/libs/patches-0.1.0.rvp`. The bundle carries both JVM classes
and `classes.dex`, so ReVanced CLI and ReVanced Manager can both load it.

`buildAndroid` skips the ABI validation task that `build` runs, because this bundle
does not keep an API dump.

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

`-b` bypasses signature and provenance verification. It is required for any bundle
that was not signed by its publisher's release workflow.

## Use with ReVanced Manager

Add the jar as a local source next to the official bundle and TheLastWallaby's
bundle. Manager keeps one patch list per source and combines them at patch time.

## Ecosystem note

ReVanced bundles (`.rvp`) and Morphe bundles (`.mpp`) are not interchangeable.
ReVanced CLI and Manager only load ReVanced bundles. A Morphe-only patch has to be
ported before it can live here, which for most Sync patches means changing the
`app.morphe` imports, the compatibility declaration, and the fingerprint style.

## Status

The bundle compiles against patcher 22.0.0 and builds clean. Both patches apply to
Sync `v23.06.30-13:39` (version code 23033). No build has been run on a device.

Fix swipe back transparency replaces the two reflection helpers in the class that
holds `Converting activity from translucent: `. The reverse conversion is matched by
the hidden method names Sync looks up, and by the sibling `(Activity)V` signature in
the same class if those names move.

Select and copy post text derives every resource id and every obfuscated reference
from the APK at patch time, and fails with a message that names the missing piece
instead of patching something unrelated. The dialog reuses the comment sheet's
layout and builder, and the row reuses a ButterKnife listener generated for the post
options sheet, so the patch adds no classes of its own.
