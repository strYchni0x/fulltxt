# Privacy Policy – FULLTXT

**Last updated:** May 2026  
**Controller:** Florian Willnat · florian@willnat.org

---

## 1. Overview

FULLTXT is an Android app for full-text search across cloud storage services. This Privacy Policy explains what data the app processes, how it is stored, and what rights you have as a user.

**Core principle:** FULLTXT processes all data exclusively on your device. There is no proprietary back-end server; no usage data, analytics, or file contents are transmitted to the app developer.

---

## 2. Data Processed

### 2.1 Account Data (Cloud Providers)

When you connect a cloud account, the following information is stored locally:

| Data | Purpose | Storage location |
|------|---------|-----------------|
| Email address / username | Display in the app, unique account identifier | Encrypted on-device database |
| OAuth token / app password | Access to the cloud provider's API | Android `EncryptedSharedPreferences` (AES-256) |
| Display name | Display in the app | Encrypted on-device database |

Supported providers: Google Drive, Microsoft OneDrive, Nextcloud, ownCloud, Dropbox, MagentaCloud, Strato HiDrive.

### 2.2 File Index

To enable full-text search, the following metadata from your cloud files is indexed locally:

- File name, file size, modification date, file path
- Text content of the file (for Office documents and PDFs)

The index is stored in an SQLite database on the device and **never leaves the device**.

### 2.3 Temporary Files

For text extraction, files are temporarily downloaded to the device's internal cache (`cacheDir`). Temporary files are deleted immediately after text extraction completes.

### 2.4 Notifications

The app displays progress notifications during active indexing operations. This requires the Android `POST_NOTIFICATIONS` permission (Android 13+).

---

## 3. Data Sharing with Third Parties

FULLTXT itself does not transmit any data to external servers. During indexing, the app communicates directly (without routing through a proprietary server) with the APIs of the respective cloud providers. Their privacy policies apply:

- **Google Drive:** [policies.google.com/privacy](https://policies.google.com/privacy)
- **Microsoft OneDrive:** [privacy.microsoft.com](https://privacy.microsoft.com/en-us/privacystatement)
- **Dropbox:** [www.dropbox.com/privacy](https://www.dropbox.com/privacy)
- **Nextcloud / ownCloud / MagentaCloud / Strato HiDrive:** Privacy policy of the respective server operator

---

## 4. Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Connection to cloud APIs |
| `ACCESS_NETWORK_STATE` | Check network type (Wi-Fi vs. mobile data) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Indexing via foreground service (file download) |
| `POST_NOTIFICATIONS` | Progress display during indexing |

The app does **not** request permissions to access local device files, location, camera, or contacts.

---

## 5. Data Retention and Deletion

All data stored by FULLTXT resides exclusively on the device:

- **Removing a single account:** Tap "Disconnect" in Settings. All index data, credentials, and OAuth tokens for that account are immediately and completely deleted.
- **Uninstalling the app:** Android automatically deletes all app data (database, SharedPreferences, cache) when the app is uninstalled.
- **Backup:** The app is configured with `android:allowBackup="false"`. App data is not backed up to the cloud via Android's backup functionality.

---

## 6. Data Security

- OAuth tokens and passwords are stored using `EncryptedSharedPreferences` (AES-256-GCM, Android Keystore).
- The on-device database contains no plaintext passwords.
- Network connections in release builds are restricted to HTTPS (Network Security Config).
- HTTP debug logging is active only in debug builds.

---

## 7. Legal Basis (GDPR)

Processing is based on **Art. 6(1)(b) GDPR** (performance of a contract / use of app features). You provide implicit consent by connecting a cloud account. You can stop processing at any time by disconnecting your account or uninstalling the app.

---

## 8. Your Rights

As a data subject, you have the following rights:

- **Access** (Art. 15 GDPR): What data is stored? → All data resides locally on your device and is visible within the app.
- **Rectification** (Art. 16 GDPR): Not applicable — the app only stores data automatically derived from the cloud.
- **Erasure** (Art. 17 GDPR): Disconnect the account or uninstall the app.
- **Data portability** (Art. 20 GDPR): Data is stored locally; export is possible via Android file manager.
- **Complaint** (Art. 77 GDPR): You have the right to lodge a complaint with a data protection supervisory authority.

---

## 9. Children

The app is not directed at children under the age of 16. We do not knowingly process data from minors.

---

## 10. Changes to This Policy

For material changes, the app version number is incremented and documented in the changelog. The current version is always available in the app repository under `PRIVACY_POLICY.md`.

---

## 11. Contact

For privacy-related questions:

**Florian Willnat**  
Email: florian@willnat.org
