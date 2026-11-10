# QueueLess KUET (Java, Android Studio Dolphin 2021.3.1 Patch 1)

Smart campus queue and token management. Students take a digital token, watch their live
position and estimated wait, and get notified when their turn is near. Operators run the
queue (call next / complete / skip), announce delays, verify tokens by QR, and see analytics.

## Build environment
| Item | Value |
|---|---|
| Android Studio | Dolphin 2021.3.1 Patch 1 |
| Android Gradle Plugin / Gradle | 7.3.1 / 7.4 |
| JDK | 11 (bundled with Studio) |
| compileSdk / targetSdk / minSdk | 33 / 33 / 24 |
| Language / UI | Java 8 syntax, XML + ViewBinding + Material 3 |
| Backend | Firebase Authentication + Cloud Firestore |

## One-time setup (required, the app needs your Firebase project)
1. Open this folder in Android Studio (File > Open) and let Gradle sync stop at the missing
   `google-services.json` error (expected until step 3).
2. In the Firebase console: create a project, then **Add app > Android** with package name
   `com.kuet.queueless`.
3. Download `google-services.json` and put it in the `app/` folder.
4. Firebase console > **Authentication** > Sign-in method > enable **Email/Password**.
5. Firebase console > **Firestore Database** > Create database (production mode).
6. Firestore > **Rules** tab > paste the contents of `firestore.rules` > Publish.
7. Sync Gradle and run on two devices/emulators (one student, one operator).

No Firestore indexes are needed (queries use equality/IN filters only; sorting is done in the app).

## Demo script (2 devices)
1. Device A: register as **Service operator** (code `KUET-OP-2026`, see `Constants.OPERATOR_CODE`).
   Menu > Add service: "Campus Copy Center", prefix `P`, 5 min.
2. Device B and a second account: register as **Student**, open the service, tap **Get token**.
3. Device A: **Call next** > student sees "It's your turn" (and a notification) > **Complete**.
4. Try: cancel a token, skip, close service, post an announcement, scan the student's QR,
   open History & analytics.

## How the hard parts work
* **No double-serving:** `services/{id}.currentTokenId` is a lock set inside a Firestore
  transaction by *Call next*. A second operator's transaction sees the lock and is rejected.
* **No duplicate tokens:** token numbers come from `services/{id}.lastTokenNumber`, incremented
  in the same transaction that creates the token. Display numbers (P-024) restart each day;
  the Firestore document id is the real identifier.
* **Legal state transitions:** `TokenStatus.canTransitionTo()` (WAITING -> CALLED/SKIPPED/CANCELLED,
  CALLED -> COMPLETED/SKIPPED, everything else final). Every transition writes a `queueEvents` document.
* **Waiting time:** people ahead x average service time. The average is a rolling average
  (70% old + 30% new) updated on every completed token. Shown as an approximate range.
* **Security:** enforced in `firestore.rules`, not by hiding buttons.

## Known limits (good to mention in your report/viva)
* Notifications are local: they work while the app process is alive. True background push needs
  Firebase Cloud Messaging + a Cloud Function.
* Operator sign-up uses a shared access code checked in the app; in production, assign the
  operator role from an admin tool or custom claims.
* The one-active-token-per-student rule is checked in the app, not in the security rules.
* Service duration uses the device clock for the "complete" time, so skewed clocks affect the average slightly.
* "Clear queue" uses a single batch (max 500 tokens).

## Package layout
```
com.kuet.queueless
  model        Token, TokenStatus, ServiceItem, AppUser
  repository   AuthRepository, ServiceRepository, TokenRepository (all queue transactions)
  util         WaitTimeCalculator, QueueWatcher, NotificationHelper, Navigator, Callbacks, ...
  ui           Login, Register, StudentHome, Token, Operator, History + adapters + BarChartView
```
