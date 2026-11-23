# Signing a release

Every release so far has been **debug-signed**, and the cost of that is paid
by whoever installs one: Android refuses to install an APK over a build
signed with a different key, so each release has meant *uninstall the old
one first* — which takes the relay rules, the goals, the tile layout and the
alert history with it, because nothing in this app is backed up.

The debug key is generated per machine. A build from a CI runner and a build
from a laptop are signed by two different keys even though both are "the
debug key", so the problem is not theoretical and not limited to upgrades
across machines.

One keystore, made once, fixes it for every release after it. It cannot fix
the upgrade *into* the first signed release: that one still needs an
uninstall, and it is the last one that will.

## Nobody but you can do this part

The commands below create a credential and then type its password into
things. That is yours to hold: the keystore and its passwords must not pass
through a transcript, a tool, or an assistant, and a key that has is a key to
throw away. Everything around it — the build config, the workflow, the
release notes — is already done and expects these four values to arrive as
environment variables.

**If the keystore is lost, it is lost.** There is no recovery and no reset.
Every future release has to be signed by the same key or it will not install
over the last one. Back it up somewhere that is not this repository and not
the laptop that made it.

## 1. Make the keystore

```
keytool -genkeypair -v -keystore release.jks -alias cuff -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` ships with the JDK; on Windows it is under
`C:\Program Files\Android\Android Studio\jbr\bin\`. It will ask for a store
password, then for the fields in the certificate — the only one anybody ever
sees is the first, and `Cuff` is a fine answer to all of them. `-validity
10000` is about twenty-seven years, which is Google's own advice: a key that
expires is an app that can no longer be updated.

Put `release.jks` anywhere **outside this repository**.

## 2. Tell a local build about it

Create `key.properties` in the repository root. It is already in
`.gitignore`, on line 5, and must stay there.

```properties
storeFile=C:/path/to/release.jks
storePassword=…
keyAlias=cuff
keyPassword=…
```

`app/build.gradle.kts` reads these through `releaseSetting`, which falls back
to the environment and then to nothing; with nothing it uses the debug key
and the build still succeeds. That fallback is deliberate — a contributor
without the key can still build — and it is also why a typo here does not
fail loudly. After the first signed build, check what you got:

```
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

The owner should be whatever you typed in step 1, not `CN=Android Debug`.

## 3. Tell the release workflow about it

Four repository secrets, under *Settings → Secrets and variables → Actions*.
Set all four or none; the workflow checks the first and uses the debug key
when it is empty.

| Secret | Value |
| --- | --- |
| `AIR_RELAY_KEYSTORE_BASE64` | the `.jks`, base64 encoded |
| `AIR_RELAY_KEYSTORE_PASSWORD` | the store password |
| `AIR_RELAY_KEY_ALIAS` | `cuff` |
| `AIR_RELAY_KEY_PASSWORD` | that key's password |

To encode the keystore:

```
base64 -w0 release.jks > release.jks.base64
```

Paste the contents of that file as the secret, then delete it. On Windows
without `base64`, PowerShell will do it:

```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Content release.jks.base64 -NoNewline
```

The next tag pushed will say *"This APK is signed with the project's release
key"* in its notes instead of the debug-key warning. If it still says the
warning, the first secret did not arrive.

## If you take this to Play

Play App Signing takes a different shape: Google holds the *app signing key*
and you hold an *upload key*, which is the one made above. The upload key can
be reset if you lose it; the app signing key cannot be, which is the point of
Google holding it. The `.aab` attached to each release is what gets uploaded.

Nothing here needs changing for that — an upload key is an ordinary keystore,
and the workflow signs the bundle with the same config it signs the APK with.
