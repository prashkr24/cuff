# Air Bridge on iOS — feasibility

Written 4 November 2026. Research-only; nothing here has been run on a device.
Every claim is tagged **[doc]** (Apple/Google primary documentation), **[obs]**
(a dated first-hand account), or **[inf]** (my inference — argued, not evidenced).

---

## Verdict

1. **The README's line is now out of date.** iOS *does* have a notification-reading API as of 26.5 — `AccessoryNotifications` — but it is EU-customers-only, needs three entitlements, and is built so that only an accessory whose firmware you wrote can use it as intended.
2. **There is a real loophole and it fits Air Bridge exactly**: the "accessory" side of the key exchange is code *you* write in *your* extension, so you can hold the keypair on the phone, decrypt the notification yourself, and buzz the Air over the existing reverse-engineered stack. Untested, and it is the path a Pebble developer rejected over App Review risk.
3. **The hard gate is geography, not technology.** `AccessoryNotifications` refuses to run for customers outside the EU. For a non-EU owner the alerts half is dead — no per-app rules, no per-person rules, no call rings; only self-scheduled buzzes survive.
4. **The health half ports cleanly.** Google Health on iOS writes 10 of the 13 record types to Apple Health, including sleep stages. HRV and skin temperature are lost; the old Fitbit Web API was switched off five days ago.
5. **Cost: ~3–4 months for the health half alone** (78% of the app is Compose and must be rewritten), and the alerts half is a research project with a coin-flip outcome and a hostile App Store.

---

## A. The notification relay on iOS

### A.1 The general APIs — all dead ends, confirmed

| Route | Verdict |
|---|---|
| Notification Service Extension | Your app's **own** remote pushes only. Mutates payloads before display. Cannot see other apps. |
| `UNUserNotificationCenter.getDeliveredNotifications` | Your app's own notifications only — it reads *your* notification centre entries. |
| `DeviceActivity` / Screen Time | Usage *durations* per app category, deliberately aggregated and privacy-shielded. No notification content, no per-event callback. |
| `SensitiveContentAnalysis` | Classifies images/video **your app already holds** for nudity. Unrelated. |
| Accessibility APIs | iOS has no AX API that enumerates or reads system notifications for a third-party app. (macOS has the AX tree; iOS does not expose it to apps.) |
| **ANCS** | See below — runs the wrong way. |

Apple Developer Forums, repeatedly: *"there's no public API to discover anything about other apps installed on the device"* ([thread 710167](https://developer.apple.com/forums/thread/710167)); a macOS-side thread concluded there is *"no sanctioned API for system-wide notification interception"* ([thread 758451](https://developer.apple.com/forums/thread/758451)). Flutter's `notification_listener` plugin is Android-only "due to iOS restrictions". **[obs]**

### A.2 ANCS — the direction question, answered

**The iPhone is the ANCS *server*. The accessory is the client.** The GATT roles are inverted from normal: iOS acts as GATT server/peripheral advertising the ANCS service (`7905F431-B5CE-4E99-A40F-4B1E122D00D0`), and the accessory connects as GATT client/central and subscribes to the Notification Source characteristic. Confirmed by [Apple's ANCS specification](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Introduction/Introduction.html), by vendor implementations ([Silicon Labs](https://docs.silabs.com/bluetooth/2.13/bluetooth-code-examples-applications/apple-notification-center-service), [TI](https://github.com/ti-simplelink/ble-sdk-210-extra/blob/master/Projects/ble/ancs/README.md)), and by [Apple Developer Forums thread 764999](https://developer.apple.com/forums/thread/764999). **[doc]**

So **an iOS app cannot be an ANCS consumer for the phone it runs on.** There is nothing for it to connect to — the service is offered outbound to accessories, not inbound to apps. An older first-hand report that ANCS is "no longer consumable in CoreBluetooth" is ~10 years old but consistent. **[obs]**

`CBPeripheral.ancsAuthorized` does **not** change this. It is a read-only property that tells your *central-role* app whether a **peripheral it is connected to** has been authorized by the user to receive notifications from this phone — i.e. it reports on the accessory's ANCS permission, introduced in [WWDC19 session 901](https://developer.apple.com/videos/play/wwdc2019/901/) alongside the user-authorization prompt. It grants your app nothing. **[doc]**

**Why this matters for the Air specifically:** if the Air's firmware implemented an ANCS client, an iPhone would buzz it with no app at all — this is exactly how Bangle.js does notifications ([Espruino "iOS Integration" app](https://github.com/orgs/espruino/discussions/2048)). It evidently does not, and you cannot add it. On Android your app is the privileged component; on iOS the *firmware* is. That is the structural reason this is harder on iOS, and it is also why InfiniTime's iOS companion [InfiniLink](https://github.com/JenJell/InfiniLink) lists phone notifications as non-functional pending ANCS work *in the watch firmware*. **[obs]**

### A.3 `AccessoryNotifications` — the new, sanctioned keyhole

This is new since the README was written and it changes the answer.

Apple shipped a DMA-driven notification-forwarding path. The settings UI ("Notification Forwarding") appeared in **iOS 26.3** ([MacRumors, 15 Dec 2025](https://www.macrumors.com/2025/12/15/ios-26-3-notification-forwarding/)); the developer API lands in **iOS 26.5**. **[doc]**

From the [AccessoryNotifications framework page](https://developer.apple.com/documentation/accessorynotifications), verbatim:

> **Important:** This framework supports iPhone only. You can develop and test an app that uses this framework on devices in any region. **Customer installations of your app can only use the framework on devices located in the EU that are signed in with an Apple Account with an EU country or region.**

**The architecture** ([Receiving iOS notifications on an accessory](https://developer.apple.com/documentation/AccessoryTransportExtension/receiving-ios-notifications-on-an-accessory)) is three app extensions:

```
system notification
      |
      v
AccessoryDataProvider ext          <- sees PLAINTEXT AccessoryNotification
  addNotification(_:alertingContext:)   your rule logic runs HERE
      | session.send(message:)
      v
[ system encrypts with keys from AccessoryTransportSecurity ext ]
      |
      v
AccessoryTransportAppExtension     <- gets CIPHERTEXT only; owns the BLE link
      |
      v
accessory decrypts with HPKE (RFC 9180) + AES-GCM
```

What the data provider receives is generous — it is the whole notification:
`title`, `subtitle`, `body`, `summary`, `sourceName`, `sourceIcon`, `contextIcon`,
`attachments`, `actions`, `identifier`, `threadIdentifier`, `deliveryDate`,
`attributes` (critical / time-sensitive / priority), plus an `AlertingContext`
carrying `shouldAlert`, `notificationCanAlert`, `isSuppressedByFocus`, `sound`,
and `kind` — **including `AlertingContext.Kind.incomingCall`**. **[doc]**

Registration is `AccessoryNotificationCenter.requestForwarding(for: ASAccessory)`, which returns `.allow` (all apps), `.limited` (a user-chosen subset), or a refusal. The user picks which apps forward, in a system prompt. **[doc]**

**The documented blocker:** the accessory must generate an XWing (ML-KEM-768 + X25519) or P-256 keypair, run HPKE key agreement, and AES-GCM-decrypt frames shaped `IV(12) || ciphertext || MAC(16)`. Apple is explicit that the phone-side transport extension is locked out:

> "Because the data is encrypted, your extension is unable to read or otherwise make sense of the data, and can only transmit it."

The Fitbit Air runs Fitbit's closed firmware. It implements Golden Gate/DTLS/CoAP, not Apple's HPKE handshake. On a plain reading, **this path is closed to Air Bridge.** That is also why both Pebble efforts needed matching firmware: an independent developer wrote HPKE into PebbleOS ([PebbleOS-Swift](https://github.com/zunda-pixel/PebbleOS-Swift), [coredevices/PebbleOS#2301](https://github.com/coredevices/PebbleOS/issues/2301)), and Amazfit shipped it to only **3** watch models, each needing new firmware ([Zepp press release, 25 Jun 2026](https://www.zepp.com/press-release/amazfit-brings-interactive-ios-notifications-to-select-smartwatches-in-europe)). **[doc]**

### A.4 The loophole — and why it fits Air Bridge better than it fits Pebble

**The accessory side of the key exchange is code you write, and nothing forces it to run on the accessory.** **[inf]**

Read Apple's own sample again. The `AccessoryTransportSecurity` extension is *your* process, on the *phone*. Apple's example calls the keypair generation "on your accessory", but the code is in the extension:

```swift
case .p256:
    let privateKey = P256.KeyAgreement.PrivateKey()
    accessoryPrivateKey = privateKey.rawRepresentation
    accessoryPublicKey  = privateKey.publicKey.rawRepresentation
```

Nothing in the API verifies that the key material ever reached a physical device. So you can:

1. Generate the "accessory" keypair **in the security extension**, stash the private key in the shared App Group keychain.
2. Hand the public key to the system; receive the encapsulated key; call `completion(.success)` without transmitting anything over BLE.
3. In the **transport extension**, decrypt the ciphertext yourself with the private key you kept — you have everything Apple's doc tells the accessory to do.

**For Air Bridge you don't even need step 3.** The app's output is a *buzz*, not a rendered notification. All the semantics live in the data provider, which already sees plaintext:

| Air Bridge feature | iOS mechanism in the data provider | Survives? |
|---|---|---|
| Per-app rules | `notification.sourceName` (+ bundle id via `identifier`) | Yes |
| Per-person rules | parse `title`/`body` exactly as the Android listener does today | Yes |
| Quiet hours / pause | your own clock check, as now — **plus** `alertingContext.isSuppressedByFocus` and `shouldAlert` for free | Yes, better |
| Pulse pattern / strength | encode as payload count or padded length; the transport ext counts messages and rings the Air accordingly | Yes, via a side channel |
| Suppress entirely | `return false` from `addNotification` and send nothing | Yes |
| Calls | `AlertingContext.Kind.incomingCall` | Yes |
| **Call rings until answered** | start the held ring on `.incomingCall`; stop on `removeNotification(identifier:)` when the call ends | **Yes — and this is a cleaner signal than Android's** |

The transport extension is the right place for the BLE work, and Apple sanctions it: it is the only one of the three that gets `bluetooth-central`, and Apple documents `transportStateRestoreIdentifier`, cold relaunch via `centralManager(_:willRestoreState:)`, and warm resume. **[doc]** That is a *better* background story than a normal app gets.

**Four things still have to be true, and none is verified:**

1. **The system must start a forwarding session.** If `deviceaccessd` requires evidence of a real accessory round-trip before activating the data provider, the whole thing stalls at step 2. Untested.
2. **AccessorySetupKit must yield an `ASAccessory` for the Air** while Google Health owns the pairing. See A.6.
3. **EU-only.** Non-negotiable, enforced by Apple Account region + device location, and a VPN does not defeat it.
4. **iOS 26.5+** on the phone.

Honesty about provenance: zunda-pixel, who shipped the first working third-party implementation of this API, **considered reading notification content directly in the extension and rejected it as carrying App Review risk** ([zunda-pixel/pebble-app](https://github.com/zunda-pixel/pebble-app)). So the idea is not novel and the person closest to the API thought it was reviewable-against. For Air Bridge the calculus differs — we never display or store content, only count pulses — but Apple's intent is unambiguous and they can close this whenever they like.

### A.5 Entitlements — not the gate I expected

All three entitlement pages (`com.apple.developer.accessory-data-provider` iOS 26.5+, `com.apple.developer.accessory-transport-security` iOS 26.5+, `com.apple.developer.accessory-transport-extension` iOS 26.2+) document **no request process, no approval step, and no manufacturer requirement** — they link to the ordinary [Adding capabilities to your app](https://developer.apple.com/documentation/Xcode/adding-capabilities-to-your-app) flow. Compare genuinely restricted entitlements like `networking.multicast`, whose pages carry an explicit "you must request this from Apple" paragraph; its absence here is meaningful. **[doc]**

Corroborated first-hand: on **21 September 2026** an independent developer with no manufacturer relationship enabled the capability in the Developer Portal web UI and his provisioning profile came back carrying all three ([zunda-pixel/pebble-app#29](https://github.com/zunda-pixel/pebble-app/issues/29)). By 22 September real iPhone notifications were rendering on a Pebble Time 2; merged 29 September as [PR #154](https://github.com/zunda-pixel/pebble-app/pull/154). A March 2026 beta regression where the Portal exposed only one of the three ([Apple forum 821150](https://developer.apple.com/forums/thread/821150), from a `com.huami.*` bundle id — Zepp) was plumbing, not policy, and was gone by September. **[obs]**

**Five-minute experiment that settles this for you:** create a throwaway App ID in your own Developer Portal and read the capability list.

### A.6 AccessorySetupKit and the already-paired problem

`requestForwarding(for:)` takes an `ASAccessory`, so [AccessorySetupKit](https://developer.apple.com/documentation/accessorysetupkit) (iOS 18+) is mandatory. [`ASDiscoveryDescriptor`](https://developer.apple.com/documentation/AccessorySetupKit/ASDiscoveryDescriptor) **requires** either `bluetoothServiceUUID` or `bluetoothCompanyIdentifier`, declared in `NSAccessorySetupBluetoothServices` / `NSAccessorySetupBluetoothCompanyIdentifiers` in Info.plist. **[doc]**

Two consequences:

- You must ship Fitbit's GATT service UUID (or Fitbit's company id) **statically in your binary**. That is a self-declared, inspectable statement that your app exists to drive Fitbit hardware — a 5.2.x flag handed to the reviewer for free.
- **Can ASK adopt a device Google Health already owns?** [`ASMigrationDisplayItem`](https://developer.apple.com/documentation/AccessorySetupKit/ASMigrationDisplayItem) exists for migrating a previously-discovered accessory into ASK, but its `peripheralIdentifier` is a `CBPeripheral.identifier` — **a per-app-installation-scoped UUID, not a device address**. Migration is designed for *your own* app moving *its own* CoreBluetooth-paired accessory. Nothing documents cross-app adoption. **Unresolved, leaning negative.** **[inf]**

Circumstantial support for "ASK is invasive and exclusive": Core Devices report the iOS app "must either use ASK or not", and adopting it requires reversing their PPoGATT role and reflashing recovery firmware on every watch in the field ([Pebble Mega Update, July 2026](https://repebble.com/blog/pebble-mega-update-july-2026)). Their words: *"Enabling ASK is going to be tough"*, *"This saga will take a while."* **[obs]**

### A.7 Call detection without the new API

If `AccessoryNotifications` is unavailable (non-EU), calls fall back to CallKit, and it is thin:

- `CXCallObserver` reports **state transitions only** — a `CXCall` carries a `UUID`, `hasConnected`, `hasEnded`, `isOutgoing`, `isOnHold`. **There is no caller number and no caller name.** Apple Developer Forums are consistent that there is no way to obtain an incoming call's phone number ([thread 745370](https://developer.apple.com/forums/thread/745370), [thread 73836](https://developer.apple.com/forums/thread/73836)). **[obs]**
- Reports of `CXCallObserver` delivering nothing while the app is backgrounded, and missing calls already in progress at cold start. **[obs]**
- WhatsApp/Telegram calls arrive as VoIP pushes to **those** apps; their CallKit reports are visible to `CXCallObserver` as anonymous call state, so you learn "a call is happening" and nothing about who or which app.

**So without `AccessoryNotifications`: per-person rules are impossible** (no identity, ever), per-app rules collapse to "some call", and ring-until-answered is the one thing that *does* survive — `hasEnded` is exactly the stop condition Android's listener reconstructs.

### A.8 Background execution for a plain (non-extension) app

Relevant only to the fallback design, and this is where Migicovsky's account is the best evidence available:

> *"If you (accidentally) close our iOS app, then your watch can't talk to app or internet."* — [ericmigi.com, 17 Mar 2025](https://ericmigi.com/blog/apple-restricts-pebble-from-being-awesome-with-iphones/) **[obs]**

`bluetooth-central` background mode plus a `CBCentralManager` restore identifier lets iOS relaunch the app on BLE events, and Apple documents the same mechanism for the transport extension (cold relaunch via `willRestoreState:`, warm resume otherwise) **[doc]**. But a user force-quitting the app from the switcher disables relaunch until they open it again, and there is no Android-style foreground service to hold the line. Migicovsky also notes developer-mode relaying disconnecting "every few minutes". **[obs]**

Net: a plain iOS app can hold a BLE link for a long time, but not with the "evidenced, not guaranteed" confidence the Android build already has — and the one force-quit that kills it is invisible to you.

### A.9 Conclusion for the alerts half

**(b), narrowing to (a) outside the EU.**

- **EU owner, iOS 26.5+, and the four unknowns in A.4 break your way:** essentially the whole feature set survives — per-app rules, per-person rules, quiet hours, patterns, and a *better* call-ring stop signal than Android has. Built on a sanctioned API used in a way Apple did not intend, so treat its lifespan as one OS release at a time.
- **Non-EU owner:** the alerts half is **impossible** in any form worth shipping. What remains is the handful of buzzes the app raises itself — the step-goal pulse and hourly nudge (drivable from HealthKit background delivery, hourly granularity), plus anything you schedule yourself. No app notifications at all. Calls only as anonymous state, background-unreliable.

---

## B. The health half

Short version: **this ports, and better than expected.**

### B.1 Does the band's data reach Apple Health?

Yes. The Fitbit app **became Google Health on 19 May 2026** — same App Store listing (id `462638897`), now "Google Health (Fitbit)", minimum **iOS 16.4**. ([Google Health Help](https://support.google.com/googlehealth/answer/17068213?hl=en)) The **Fitbit Air is first-class on iPhone**: Google's launch post names "Android 11 or higher and iOS 16.4 or higher" ([blog.google](https://blog.google/products-and-platforms/devices/fitbit/fitbit-air/)), and an iOS release note for v5.02 mentions "improved in-workout handling for Fitbit Air users". **[doc]**

Since **Google Health v5.05 (iOS, ~3 Aug 2026)** the sync is **two-way**, and the write list is unusually generous. From [Google's own help page](https://support.google.com/googlehealth/answer/17037331?hl=en):

**Writes to Apple Health:** steps, VO2 max, floors, active calories, distance, exercise, exercise routes, body temperature, **sleep session, sleep stages**, heart rate, **oxygen saturation**, respiratory rate, **resting heart rate**, blood glucose, weight, fat %, nutrition, water, mindfulness, cycle health.

**Reads only (never written back): heart rate variability, skin temperature.**

### B.2 Record type mapping

| Health Connect | HealthKit | Shape differs? | Google Health writes it? |
|---|---|---|---|
| `StepsRecord` | `.stepCount` | ~1:1. Watch for double-counting against iPhone motion in raw sample queries; use statistics queries. | Yes |
| `HeartRateRecord` | `.heartRate` | HC packs a series into one record; HK is one sample per reading. Flatten. | Yes |
| `RestingHeartRateRecord` | `.restingHeartRate` | Not Apple-Watch-only in practice — third-party writes coexist. | Yes |
| `SleepSessionRecord` + stages | `.sleepAnalysis` / `HKCategoryValueSleepAnalysis` | **Structurally different.** HC = one session holding a stage list. HK = a flat set of overlapping category samples (`inBed` + many stage spans). You reconstruct sessions by clustering. | Yes (stages listed separately from session) |
| `HeartRateVariabilityRmssdRecord` | `.heartRateVariabilitySDNN` | **Different statistic.** SDNN vs RMSSD are not interconvertible without beat-to-beat intervals. See B.3. | **No — read-only direction** |
| `TotalCaloriesBurnedRecord` | *(no single type)* `.activeEnergyBurned` + `.basalEnergyBurned` | HK splits active/basal deliberately. | **Active only** — no basal, so no total |
| `ActiveCaloriesBurnedRecord` | `.activeEnergyBurned` | 1:1 | Yes |
| `ExerciseSessionRecord` | `HKWorkout` / `HKWorkoutActivityType` | Different activity enums; needs a mapping table. Routes supported. | Yes |
| `DistanceRecord` | `.distanceWalkingRunning` (+ cycling/swimming) | HC has one generic distance; HK splits by modality — lossy for a daily total. | Yes |
| `OxygenSaturationRecord` | `.oxygenSaturation` | HK uses a 0–1 fraction, not 0–100. | Documented yes — **but see B.4** |
| `SkinTemperatureRecord` | *(none usable)* | `appleSleepingWristTemperature` is **explicitly read-only to third parties** and Apple-Watch-only; `bodyTemperature` is a different quantity. | **No — read-only direction** |
| `RespiratoryRateRecord` | `.respiratoryRate` | 1:1 | Yes |
| `WeightRecord` | `.bodyMass` | 1:1 | Yes (+ `.bodyFatPercentage`) |
| `NutritionRecord` **(the one write)** | `HKCorrelationTypeIdentifier.food` wrapping `dietaryEnergyConsumed`, `dietaryProtein`, `dietaryCarbohydrates`, `dietaryFatTotal`, … | HC = one record with optional nutrient fields; HK = one `HKQuantitySample` per nutrient, grouped in an `HKCorrelation`, name in `HKMetadataKeyFoodType`. More verbose, fully expressive. | Google Health **reads** nutrition from Apple Health, so your writes round-trip back into it. |

Sources: [`HKQuantityTypeIdentifier`](https://developer.apple.com/documentation/healthkit/hkquantitytypeidentifier), [`HKCategoryValueSleepAnalysis`](https://developer.apple.com/documentation/healthkit/hkcategoryvaluesleepanalysis), [`appleSleepingWristTemperature`](https://developer.apple.com/documentation/healthkit/hkquantitytypeidentifier/applesleepingwristtemperature), [`HKCorrelationTypeIdentifier.food`](https://developer.apple.com/documentation/healthkit/hkcorrelationtypeidentifier/food).

### B.3 HRV — the one real loss, with a twist

The brief asked whether both platforms use SDNN. **They do not.** Health Connect's type is `HeartRateVariabilityRmssdRecord` (RMSSD); HealthKit's long-standing type is `heartRateVariabilitySDNN` — Apple's abstract: *"measures the standard deviation of heartbeat intervals"*. These are different time-domain statistics over different windows and are **not interconvertible**. Writing RMSSD into the SDNN type corrupts the user's SDNN series. **[doc]**

Google says as much in explaining the gap: Google Health and Apple Health "measure HRV using different formulas", so they do not swap the values. **[obs, secondary]**

**The twist:** `HKQuantityTypeIdentifier.heartRateVariabilityRMSSD` **exists as of iOS 27.0** — confirmed from Apple's own symbol-graph JSON. But it has **no abstract and no discussion**, does not appear in the Vital Signs topic group, and is absent from Apple's "What's new in HealthKit". Whether it is third-party-writable (or read-only like `appleSleepingWristTemperature`) is unknown, and **Google is not writing it** — their Apple Health table still lists HRV as read-only. **[doc on existence; uncertain on everything else]**

So: **HRV is unavailable on iOS today**, and the HRV screen, the Vitals tile's HRV slot, and any readiness band built on it have no data source short of the cloud API.

### B.4 Two things to verify before promising them

- **SpO2.** On Android you *established from the device* that Fitbit is not granted `WRITE_OXYGEN_SATURATION`, so blood oxygen is empty. On iOS, Google's docs put oxygen saturation in the **write** column. That is a documentation claim about a metric where Google's documentation has already been wrong on the other platform. Check before designing around it.
- **Sleep stages.** Google lists "Sleep stages" as a line item distinct from "Sleep session", which strongly implies real `asleepCore`/`asleepDeep`/`asleepREM` samples rather than one blob. Worth eyeballing once. **[inf]**

### B.5 Two API differences that change code, not features

- **Deletion.** HealthKit apps can delete **only samples they themselves saved** — `deleteObjects(of:predicate:)` is documented as *"Deletes objects saved by this application"*, and `delete(_:)` fails `errorInvalidArgument` otherwise. **But check the Android premise too**: HANDOVER says "deleting goes through Health Connect and takes the record with it, **whichever app wrote it**". If Health Connect's `deleteRecords` is in fact also scoped to your own records, this is not a regression at all — the food log only ever deletes entries it wrote. Worth confirming, since it is written down as settled.
- **Authorization is invisible.** HealthKit deliberately never tells you whether a **read** permission was granted; denied reads return empty sets. "No HRV data" and "you denied HRV" are indistinguishable at runtime. This is the same class of bug as `READ_DISTANCE` — and HANDOVER's solution (log the ungranted types, say "distance not allowed yet") **cannot be ported**: on iOS you genuinely cannot tell. The copy has to become "no data found — check Health › Sharing › Apps".

### B.6 Background reads

`HKObserverQuery` + `enableBackgroundDelivery(for:frequency:)`, needing the `com.apple.developer.healthkit.background-delivery` entitlement (an ordinary Xcode capability). **[doc]** Four facts that shape the design:

- Frequencies are `.immediate` / `.hourly` / `.daily` / `.weekly`, and they are **upper bounds**. Apple: *"Some sample types have a maximum frequency of hourly. The system enforces this frequency transparently. For example, on iOS, `stepCount` samples have an hourly maximum frequency."* **So the step-goal buzz becomes hourly at best** — it currently polls every 15 minutes.
- Observer queries must be registered in `application(_:didFinishLaunchingWithOptions:)`.
- They carry **no payload**; you follow up with an `HKAnchoredObjectQuery` for deltas.
- **You must call the update's completion handler.** Fail three times and HealthKit silently stops waking you forever. This is the iOS equivalent of the `startForeground`-within-five-seconds invariant and belongs in the invariants list.

Not supported in the Simulator.

### B.7 The Fitbit Web API is gone

**Switched off at the end of October 2026** — Google's two primary sources disagree by three days (`dev.fitbit.com` says 30 Oct "stop functioning"; `developers.google.com/health/about` says 2 Nov, returning HTTP `410 Gone`). Either way it is off today. Legacy OAuth tokens cannot be migrated. **[doc]**

Its successor is the **Google Health API**, and for the four things HealthKit cannot give you it is excellent — the Fitbit Air is listed as compatible for `heart-rate-variability` and `daily-heart-rate-variability`, `oxygen-saturation`, `daily-respiratory-rate`, and `daily-sleep-temperature-derivations` (the nightly relative delta, which is what Fitbit actually surfaces; raw `skin-temperature-sensors` is Pixel-Watch-only). `nutrition-log` supports create/update/batchDelete. Intraday data is now available for all relevant types with **no separate application process** — the old Fitbit intraday hoop is gone. Rate limits are **300 requests/min/user**, far better than the legacy 150/hour. ([data types](https://developers.google.com/health/data-types), [rate limits](https://developers.google.com/health/rate-limits), [newsletters](https://developers.google.com/health/newsletters)) **[doc]**

**The catch:** every Google Health scope is **Restricted**, which means OAuth verification *plus* an annual third-party **CASA security assessment** costing **$500–$4,500/yr**; unverified apps are hard-capped at **100 users** ([app verification](https://developers.google.com/health/app-verification)). For a one-person app the 100-user cap is survivable — add yourself as a test user and click through the unverified warning — but Google's Health-API page, unlike their general OAuth policy, **states no personal-use exemption**. That is the single highest-value experiment in this document: it decides whether the cloud fallback is free or costs four figures a year.

It is also a *cloud* source: freshness is bounded by when the band last synced through the phone, and no latency guarantee is documented. Webhooks exist for most types and are the right lever.

---

## C. The transport

### C.1 Golden Gate has Swift bindings — and is archived

[github.com/Fitbit/golden-gate](https://github.com/Fitbit/golden-gate): C core, **bindings for Kotlin *and* Swift**, supported platforms explicitly including "iPhones and iPads", topic tag `corebluetooth`, and `Cartfile` / `Cartfile.private` / `Cartfile.resolved` at the root (Carthage — the iOS build was real, not aspirational). **Apache-2.0**, which is compatible and already how `:air` is licensed. **[doc]**

**It was archived by Fitbit on 20 June 2025 and is read-only.** 417 commits, no further maintenance. **[doc]**

So the iOS story for the stack is better than the brief feared: you are not porting a JNI layer, you are **picking up bindings upstream already wrote**. Building `libgg`/`libxp` for `arm64` device + `arm64-sim` is an ordinary CMake cross-compile of a C library that already claims iOS support. The risk is not feasibility, it is bit-rot: a 2025-archived Carthage-era project against Xcode 17+ and modern Swift concurrency will need work, and nobody upstream will take your patches.

Note for the README: `air/src/main/jniLibs/` actually carries **three** ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`), not one — it is the APK packaging that restricts to arm64. Irrelevant to iOS, but the "prebuilt, one ABI" framing is slightly off.

### C.2 The credential story — much better news than the README implies

The README says *"resources that exist behind Google Health's credentials answer 4.01"*, which reads as though Air Bridge's DTLS session depends on something extracted from Google Health. **It does not.** Reading `air/src/main/kotlin/com/fitbit/goldengate/bindings/dtls/`:

- `TlsKeyResolverRegistry` installs exactly two resolvers by default: `HelloTlsKeyResolver` (key id `"hello"`) and `BootstrapTlsKeyResolver` (key id `"BOOTSTRAP"`).
- `BOOTSTRAP_KEY` is a **hardcoded 16-byte constant in Apache-2.0 open-source code** (`BootstrapTlsKeyResolver.kt`, `0x81 0x06 0x54 0xE3 …`).
- `AirLink.kt` builds its session as `Stack(..., DtlsSocketNetifGattlink(), false)` with **no custom resolver** — so it handshakes on the public bootstrap PSK. The only custom resolver in the codebase is `RefusingTlsKeyResolver` (`resolveKey → null`), used deliberately to *fail* a handshake during channel handoff.

**So the DTLS handshake needs no secret at all.** What Google Health's credentials gate is *CoAP resource authorization* — privileged paths answer `4.01` to a bootstrap-keyed peer, which is exactly the observation the README records (and the same probe that proved the LED has no resource: `4.04` for non-existent vs `4.01` for existing-but-unauthorized).

**This is the most important finding in section C:** there is nothing to extract, nothing to steal from Google Health's keystore, and therefore **no iOS-specific credential blocker**. Everything Air Bridge does today — `/settings` writes, battery, device info — works on a bootstrap session, and a bootstrap session is reproducible from open-source constants on any platform.

### C.3 CoreBluetooth — can it do the rest?

| Requirement | iOS |
|---|---|
| GATT write / notify on custom characteristics | Yes. Ordinary `CBPeripheral` work. |
| DTLS over GATT | Yes — DTLS here runs *inside* Golden Gate's lwIP/UDP stack over the Gattlink characteristic. It is payload bytes to CoreBluetooth. No L2CAP channel involved, so no `CBL2CAPChannel` restrictions apply. |
| Connect to a band paired elsewhere | **The real open question.** iOS has no public "bonded devices" list and no address-based connect. You must discover by advertisement and reconnect by `CBPeripheral.identifier`, which is **scoped to your app installation**. Whether the Air advertises connectably while bonded to Google Health, and whether iOS will let a second central establish a link, is **untested**. Android's `BluetoothAdapter.bondedDevices` + `device.address` path — which is what `AirLink.kt` uses — has **no iOS equivalent**. |
| Background | `bluetooth-central` + state restoration; see A.8. Better inside a transport extension (A.4) than in a plain app. |

**The two-clients-one-session trap gets worse, not better.** The Android build already fights Google Health for the Air's single DTLS session, and `AirLink.kt` carries a whole handoff mechanism (`RefusingTlsKeyResolver`, `AirChannelMonitor`, the "Google Health answered the handshake too; both stall" path) to hand the tracker channel back politely. On iOS you have far less visibility — no GATT server callbacks on another app's traffic, no `dumpsys`, no bond list — so the same arbitration has to be rebuilt mostly blind.

---

## D. Cross-platform options

### D.1 How much of this codebase is actually portable

Measured, not estimated (`app/src/main/kotlin`, 79 files, 20,037 lines):

| | Files | Lines | Share |
|---|---|---|---|
| Imports `android.*` / `androidx.*` | 69 | 19,494 | **97%** |
| No platform import at all | 10 | 1,277 | 3% |
| …of which touch Compose | 50 | 15,563 | **78%** |
| …of which touch Health Connect | — | 3,650 | 18% |

The genuinely platform-agnostic set is exactly ten files:

```
RelayRulePolicy.kt          262   the rule hierarchy — the heart of the app
Tiles.kt                    234   grid model, five sizes over six columns
SampleHealth.kt             202   the made-up person for Edit tiles
SleepScore.kt               176   four-component score against your own nights
HealthTrend.kt              121   trendOf, thirds-vs-thirds, the IQR "usual" band
HealthZones.kt               75   heart-rate zones, resting excluded
StepGoal.kt                  72
RelayCoalescer.kt            57
RelayConversationPolicy.kt   43
CalendarAlertFilter.kt       35
                          -----
                          1,277
```

Plus `:air` (128 files, 8,292 lines): 120 files carry no `android.*` import, but those are the **Golden Gate JNI bindings** — 77 `external fun` declarations whose package names *"can never be renamed"* because they bind by JNI symbol. They are not portable; they are **replaceable**, by upstream's Swift bindings (C.1). The genuinely Android-bound part of `:air` is 8 files / 2,517 lines, of which `AirLink.kt` alone is **1,625** — all `BluetoothGatt`, `ScanFilter`, `BroadcastReceiver`, `Handler`/`Looper`. That file is a full rewrite on any other platform.

**So the honest number is ~1,300 lines of reusable logic out of ~28,300.** Under 5%. This is a thoroughly Android-shaped codebase, and HANDOVER's own design notes are why: the tile grid, the chart toolkit (`ChartAxes`, `ChartWindow`, `Modifier.scrubbable`, labels drawn into the Canvas rather than laid out as composables), the permission flows, the foreground service — the value is in decisions expressed *through* the platform.

### D.2 The three options

**Kotlin Multiplatform.** Shares the 1,277 lines. That is the whole prize, and they are the files you would least mind rewriting — pure functions with JVM tests, a few hundred lines each. KMP's cost is a build-system conversion of a working Android app, an `expect`/`actual` seam through every storage and platform call, and Swift interop that is still awkward for coroutines and sealed hierarchies. **Not worth it at this ratio.** KMP earns its keep when the shared core is 40–60% and both apps are maintained forever; here it is 5%.

**Flutter.** `master` already holds the Flutter proof of concept, and the project left it deliberately. HANDOVER and README both record what it cost: the transport moved in-tree *because* of the rewrite; icons were PNG-compressed and decoded back "which was the Flutter channel showing through"; `List<Map<String, String>>` returns still litter `RelayContacts` and `RelayRecentCallers` as channel leftovers; `AirResult`/`AirSink` in `AirLink.kt` are explicitly *"the shape the Flutter MethodChannel used, kept deliberately"*. **Going back would undo a decision made nine days ago with reasons recorded on the day.** And it solves nothing here: Flutter cannot implement an `AccessoryDataProvider` extension, cannot call HealthKit without a plugin, and leaves the hard 90% exactly where it is.

**A separate native Swift app. This is the right answer** if anything is built. The two halves have almost nothing in common across platforms; the only real sharing is *design* — the boards on the canvas, the thirty-five screens' worth of reasoning — which transfers to SwiftUI for free and is where the project's actual value sits. SwiftUI is close enough to Compose that `HealthScreen.kt`'s tile grid and the chart toolkit translate structurally rather than conceptually.

---

## E. App Store reality

### E.1 It is not 2.5.1

A common misreading: **"Apps may only use public APIs"** (2.5.1) means **Apple's** public APIs. Driving a reverse-engineered *Fitbit* protocol over CoreBluetooth uses only public Apple API. 2.5.1 is not the hook. Nor is "reverse engineering" mentioned anywhere in the [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) — that lives in the Developer Program License Agreement and concerns *Apple's* software. **[doc]**

### E.2 It is 5.2.2, and Fitbit's ToS is unusually explicit

> **5.2.2** "If your app uses, accesses, monetizes access to, or displays content from a third-party service, ensure that you are specifically permitted to do so under the service's terms of use. **Authorization must be provided upon request.**"

And the [Fitbit Terms of Service](https://support.google.com/product-documentation/answer/14816019?hl=en):

> "You may not connect to the Fitbit Service with any device that is not manufactured, distributed, or sold by Fitbit itself … or any unauthorized application or third-party connection."
> "(5) circumvent any technological measure implemented by Fitbit"
> "(7) modify, decompile, disassemble, reverse engineer, tamper with…"

There is an arguable defence — Air Bridge talks to a band over local BLE and arguably never touches "the Fitbit Service" (the cloud). **App Review will not adjudicate that nuance.** 5.2.2 puts the burden on you to produce Google's written permission, and the reviewer reads the ToS, not your reading of it.

Closest dated precedent: an app controlling **VanMoof** e-bikes, rejected under **5.2.1** with App Review's reply reported as *"your app … is marketed to control external hardware from VanMoof, without the necessary authorization"* ([Apple forum 105345](https://developer.apple.com/forums/thread/105345)) — second-hand quote, but the form of words maps onto Air Bridge almost exactly. **[obs, secondary]** I found **no** dated case of a rejection specifically for a reverse-engineered BLE protocol, and **no** DMCA or C&D history against Fitbit reverse-engineering projects (Gadgetbridge has shipped Fitbit support on Android for years without visible consequence — mild positive evidence, not proof).

Recall also A.6: `NSAccessorySetupBluetoothServices` would ship Fitbit's service UUID statically in your Info.plist. You would be declaring the thing 5.2.2 asks about.

**MFi is a non-issue.** Apple [QA1657](https://developer.apple.com/library/archive/qa/qa1657/_index.html): *"Bluetooth low energy accessories do not interface with the External Accessory framework and are not required to be MFi compliant."* MFi gates iAP/iAP2 via `ExternalAccessory`, not BLE via CoreBluetooth. **[doc]**

And note the entitlements themselves are *not* a review problem: Zepp's app uses them (almost certainly — `com.huami.NotificationForwardingDemo` in Apple's forums, and a pairing flow that exactly matches `requestForwarding(for:)`) and is a normal App Store app. **They have authorization for their own hardware. That is the difference.**

### E.3 Notarization is the interesting asymmetry

Apple's [Changes for apps in the European Union](https://developer.apple.com/support/apps-in-the-eu): Notarization is a **baseline review** focused on security, privacy and device integrity, evaluated against the Notarization Review Guidelines — **which Apple itself describes as "a subset of the App Review Guidelines"**, covering Accuracy, Functionality, Safety, Security and Privacy.

**Intellectual property (5.2.x) is not in that subset.** **[doc, medium-high confidence — the Notarization Review Guidelines document was not read verbatim]**

So an app App Review would reject under 5.2.2 could plausibly still be **notarized** and distributed via an EU alternative marketplace. Corroborated anecdotally by a developer write-up titled ["App Review banned our core mechanic. Notarization let it through"](https://dev.to/temer/app-review-banned-our-core-mechanic-notarization-let-it-through-and-caught-something-else-3gef). **[obs]** Caveats: "functions as promised" is elastic; Google could complain to Apple directly outside the process; and notarization is Apple declining to police Fitbit's ToS, **not permission** — the contractual exposure is unchanged.

**Fees are no longer a barrier.** The per-install Core Technology Fee was abolished and replaced (effective 1 Oct 2026) by a **5% Core Technology Commission on digital transactions**. A free app with no digital transactions owes **0%**. ([Apple Newsroom, Aug 2026](https://www.apple.com/newsroom/2026/08/apple-announces-changes-for-apps-in-the-european-union/)) The steep eligibility criteria ($1M letter of credit, or 1M first annual installs, etc.) apply to *operating* a marketplace or self-hosting via Web Distribution — distributing *through* someone else's marketplace should need only notarization plus the EU terms. **[inf — verify]**

### E.4 Sideloading, which is probably what you actually want

- **AltStore PAL is no longer EU-only** — [AltStore's developer page](https://developer.altstore.io/) now lists **EU, Japan and Brazil**. Install still requires the device to be physically in a supported region on a matching Apple Account; a VPN does not defeat it. **[doc]**
- **Paid developer account self-signing**: 1-year provisioning profiles, works worldwide, no review, no entitlement questions. Annual re-sign. This is what the Rebble community already does for Pebble, and **for a personal app it is the realistic answer.**
- Free Apple ID: 7-day profiles, ~3 device UDIDs per window. A treadmill.
- **TestFlight**: 10,000 external / 100 internal; 90-day build expiry; **Beta App Review is required for the first build of each version to external testers**. Internal-only (100 testers) is the one review-free lane but requires each tester to be on your App Store Connect team. *(All TestFlight figures from third-party sources, not Apple's own current page.)* **[obs, secondary]**

**The geography rhymes.** Both the sanctioned notification API *and* the sanctioned alternative-distribution routes are region-gated. If the owner is not in the EU, both doors close at once, and the practical set is: **paid-account self-signing, health half only.**

### E.5 Prior art, briefly

- **Gadgetbridge** is Android-only for a **resourcing** reason, stated in their own [FAQ](https://gadgetbridge.org/faq/): *"built with a lot of Android specific code and dependencies"*, a port *"would require a complete rewrite, probably even in a different programming language"*, and given *"our huge codebase… this is very unlikely to happen."* **They do not claim iOS makes it impossible.** Do not cite Gadgetbridge as evidence of impossibility.
- **Pebble / Core Devices** is the central prior art and the news is sobering: as of mid-October 2026 the official iOS notification-forwarding work is an **unmerged draft PR by a community contributor** ([coredevices/mobileapp#455](https://github.com/coredevices/mobileapp/pull/455), opened 10 Oct 2026, default-off, *"No physical-watch proof is claimed here"*), blocked on firmware. The manufacturer has not shipped it; an independent developer beat them to it by three weeks. *(Core Devices' last ~4 weeks were not checked — search budget ran out.)*
- **Amazfit/Zepp** shipped first, to **3 watch models**, EU-only, needing new firmware — which independently confirms that this feature lives or dies on accessory firmware.
- **No open-source iOS app reads system notifications.** Confirmed from several angles. That is the whole reason `AccessoryNotifications` is extension-based, end-to-end encrypted, per-app consented, and EU-gated: Apple built one keyhole and shaped it so only firmware authors fit through.

---

## What it would cost

Solo, part-time, in the style this project is already built in (design board first, then build).

### Path 1 — Health half only, native SwiftUI *(the only path that does not gamble)*

| Work | Estimate |
|---|---|
| HealthKit reader: 12 types, authorization, anchored queries, sleep-session reconstruction from flat samples | 2–3 wk |
| `HKObserverQuery` background delivery + the completion-handler discipline | 3–4 d |
| Port the 1,277 lines of pure logic (trend, sleep score, zones, tiles model) + their tests | 3–5 d |
| Chart toolkit in SwiftUI Canvas — `bandsOf`, axes, `ChartWindow`, scrubbing, readouts | 2–3 wk |
| Tile grid, 13 kinds × 5 sizes, Edit tiles, packing | 2 wk |
| ~20 detail screens | 3–4 wk |
| Food log, saved foods, `HKCorrelation` write + delete-your-own-only | 1 wk |
| Goals, permissions, onboarding, empty states, the "cannot tell denied from empty" copy | 1 wk |
| Device tab minus everything BLE | 2–3 d |
| **Total** | **~10–14 weeks** |

Losses vs Android: HRV, skin temperature, total (vs active) calories, live heart rate from the band, and both self-raised buzzes.

### Path 2 — add the alerts half via `AccessoryNotifications` *(EU only, speculative)*

| Work | Estimate |
|---|---|
| **Spike first**: do the four unknowns in A.4 hold? ASK adopts the Air; forwarding session activates with a phone-side keypair | **1–2 wk, and it may simply fail** |
| Build `libgg`/`libxp` for iOS arm64 + sim; revive archived Carthage-era Swift bindings | 1–2 wk |
| Rewrite `AirLink.kt` (1,625 lines) against CoreBluetooth: scan, connect, Gattlink, session arbitration with Google Health, held-session ring | 3–4 wk |
| Three app extensions, App Group, keychain, entitlements, the security-extension keypair | 1–2 wk |
| Rules engine in the data provider; pulse encoding across the extension boundary | 1–2 wk |
| Alerts UI: rules, people, schedule, activity | 2–3 wk |
| **Total on top of Path 1** | **~9–15 weeks, contingent on the spike** |

### Path 3 — Google Health API for HRV/SpO2/temperature

OAuth + Cloud project + a sync layer: **1–2 weeks**, *after* establishing whether a personal app needs the $500–4,500/yr CASA assessment. Do that check before writing any code.

**What I would actually do:** spend **two days** on experiments 1, 2 and 5 below. They cost nothing and they decide between a 3-month project, a 6-month project, and no project.

---

## Open questions / experiments that would settle them

Cheapest and most decisive first.

1. **Is the owner in the EU, on an EU Apple Account?** Zero cost, and it alone decides whether the alerts half is worth a single further thought. Everything in A.4 is moot otherwise.
2. **Are the three entitlements self-service?** Create a throwaway App ID in the Developer Portal and read the capability list. **5 minutes, definitive.** Evidence says yes.
3. **Does `AccessoryNotifications` activate with a phone-side keypair?** The core gamble. Build a minimal three-extension app, generate the "accessory" keypair in the security extension, never transmit it, and see whether `addNotification` is ever called. **This is the single experiment the whole alerts half rests on.** Needs an EU device on iOS 26.5+.
4. **Can AccessorySetupKit adopt the Air while Google Health owns it?** Declare the Air's service UUID, run the ASK picker with the band paired in Google Health, and see whether it appears and yields an `ASAccessory`. Partly answerable by reading the [WWDC24 session 10203](https://developer.apple.com/wwdc24/10203/) transcript on migration, which was not fetched for this report.
5. **Does the Google Health API have a personal-use exemption from CASA?** Create a Cloud project, add yourself as a test user, request `health_metrics_and_measurements.readonly`, and see whether consent completes behind the unverified-app warning. **Decides whether the HRV fallback is free or four figures a year.**
6. **Does Fitbit Air SpO2 actually land in Apple Health?** Health › Browse › Respiratory › Blood Oxygen › Data Sources, look for a "Google Health" source with real samples. 15 minutes on any iPhone with an Air. Documented-but-empty is the known failure mode here.
7. **Does Google Health write real sleep *stages* to HealthKit**, or one `asleepUnspecified` blob? Same check on Health › Sleep › Data Sources.
8. **Will iOS let a second central connect to the Air while Google Health holds it**, and does the Air advertise connectably when bonded? Needs a CoreBluetooth scratch app. If this fails, the alerts half dies regardless of everything in A.
9. **Is `heartRateVariabilityRMSSD` (iOS 27) third-party writable**, and will Google adopt it? Attempt `requestAuthorization(toShare:)` and see whether it throws.
10. **Does Health Connect actually permit cross-app deletion?** Re-check the Android premise in HANDOVER ("whichever app wrote it"). If it is also self-scoped, the HealthKit restriction is not a regression and that line should be corrected.
11. **Did Core Devices ship iOS notification forwarding in the last 4 weeks?** repebble.com/blog and the Pebble app's App Store version history. If they shipped and documented it, that is the best possible field guide for Path 2.

---

## Corrections this research suggests for the existing docs

- **README, "Limits worth knowing":** *"iOS does not let an app read other apps' notifications"* is no longer unconditionally true. As of iOS 26.5 there is `AccessoryNotifications` — EU-only, accessory-firmware-shaped, but real.
- **README, "Two things this hardware cannot do":** the phrasing *"resources that exist behind Google Health's credentials"* implies the app's own session depends on Google Health credentials. It does not — `AirLink.kt` handshakes on the public `BOOTSTRAP` PSK hardcoded in Apache-2.0 Golden Gate. The 4.01s are CoAP *resource authorization*, a separate layer. Worth rewording; it changes how anyone reads the portability question.
- **README, "Build":** `air/src/main/jniLibs/` ships three ABIs (arm64-v8a, armeabi-v7a, x86_64); it is the APK that packs only arm64.
- **HANDOVER, "The food log":** *"deleting goes through Health Connect and takes the record with it, whichever app wrote it"* — worth re-verifying (see experiment 10).
