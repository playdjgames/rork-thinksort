# ThinkSort – Google Play Console answer sheet

Copy-paste answers for every Play Console form. Package: `com.playdjgames.thinksort`.
Listing text lives in `.play/listing/en-US/listing.json`; 512×512 icon in `.play/listing/en-US/icon-512.png`.

---

## 1. Create app (play.google.com/console → Create app)
| Field | Answer |
|---|---|
| App name | ThinkSort |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | Free (the unlock is an in-app purchase) |
| Developer Program Policies | ✅ accept |
| US export laws | ✅ accept |

## 2. First upload (Testing → Internal testing → Create new release)
- Choose **Use Google-generated key** (Play App Signing) when asked.
- Upload the signed `.aab` (Rork builds it; ask the agent for the download link).
- Release name: `1.0 (1)`
- Release notes: see `releaseNotes` in listing.json.
- Testers: create an email list with your own Google account.

---

## 3. App content (Policy → App content)

### Privacy policy
`https://playdjgames.com/privacy`

### App access
**All functionality is available without any special access.**
(No login. The 7-day trial starts on device; reviewers get a fresh trial.)

### Ads
**No, my app does not contain ads.**

### Content rating (IARC questionnaire)
- Email: your contact email
- Category: **All other app types** (utility/productivity)
- Violence, sexuality, language, controlled substances, gambling, horror: **No** to all
- Does the app allow users to interact or exchange content? **No**
- Does the app share the user's current physical location with other users? **No**
- Does the app allow users to purchase digital goods? **Yes**
- Is the app a web browser or search engine? **No**
- Is it primarily news or educational? **No**
Expected result: Everyone / PEGI 3.

### Target audience and content
- Target age groups: **18 and over** only
- Could the app unintentionally appeal to children? **No**

### News app
**No**

### COVID-19 contact tracing / status
**My app is not a publicly available COVID-19 app**

### Data safety
**Overview**
- Does your app collect or share any of the required user data types? **Yes**
- Is all user data collected encrypted in transit? **Yes**
- Do you provide a way for users to request that their data be deleted? **Yes** (by contacting support; purchase records are deleted from RevenueCat)
- Account creation: **My app does not allow users to create an account**

**Data types – select only:**
- Financial info → **Purchase history**
  - Collected: **Yes** · Shared: **No**
  - Processed ephemerally: **No**
  - Required or optional: **Required** (needed to unlock the app)
  - Purposes: **App functionality**

Everything else: **not collected**. Why that's accurate:
- Tasks, notes, brain dumps, shopping lists → saved only on the phone, never sent anywhere.
- Calendar → read on device to show today's events; never transmitted.
- Microphone → voice dump uses Android's built-in speech recognizer; the app keeps only the text on the phone.
- No analytics, crash reporting, advertising ID, location, or contacts.

> If Google later flags "Device or other IDs", add **Device or other IDs → Collected, not shared, App functionality** (RevenueCat's anonymous app user ID).

### Government apps
**No**

### Financial features
**My app doesn't provide any financial features**

### Health apps
**My app does not have any health features.** ThinkSort is a to-do/organization tool; the listing says it does not assess, diagnose or treat anything.
(If a reviewer pushes back, the closest category is "Productivity / organization", not medical.)

### Permissions declarations
No sensitive-permission forms are needed. Permissions used: notifications, microphone (voice dump), read calendar, run at startup (restore reminders), internet (purchases only). No SMS, call log, location, exact alarms, all-files or accessibility.

---

## 4. Store presence

### Main store listing
| Field | Value |
|---|---|
| App name | ThinkSort: Brain Dump Planner |
| Short description | Dump everything on your mind. Get one clear next step. Private and offline. |
| Full description | `fullDescription` in listing.json |
| App icon | `.play/listing/en-US/icon-512.png` |
| Feature graphic (1024×500) | **Needs to be made** |
| Phone screenshots (2–8) | **Needs to be captured** from the Android app |

### Store settings
- App category: **Productivity**
- Tags: To-do list, Productivity, Planner, Notes
- Contact email: **your support email** (required)
- Website: `https://playdjgames.com`
- External marketing: your choice

---

## 5. Monetize → Products → In-app products → Create product
| Field | Value |
|---|---|
| Product ID | `thinksort_lifetime` (must match exactly) |
| Name | ThinkSort Lifetime |
| Description | Unlock ThinkSort for good. One payment, no subscription. |
| Default price | $29.99 USD (same as the iPhone app) |
| Status | **Activate** |

> You can only create in-app products after a build containing Google Play Billing has been uploaded (step 2) **and** a payments profile is set up.

## 6. Connect RevenueCat to Google Play
RevenueCat app **ThinkSort (Android)** (`app1a1f1c11f3`) is already created with `thinksort_lifetime` linked to the `thinksort_pro` entitlement.
1. Follow https://www.revenuecat.com/docs/service-credentials/creating-play-service-credentials
2. Create a Google Cloud service account, enable **Google Play Android Developer API** and **Google Play Developer Reporting API**.
3. Play Console → Users and permissions → invite the service account email with: *View app information*, *View financial data*, *Manage orders and subscriptions*.
4. Upload the JSON key in RevenueCat → ThinkSort (Android) → Service account credentials. It can take up to 36 hours to show "Valid credentials".

## 7. New personal developer accounts only
Google requires a **closed test with at least 12 testers opted in for 14 days in a row** before you can apply for production. Internal testing doesn't count.
