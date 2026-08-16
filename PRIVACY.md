# TrustTap privacy and safety behavior

TrustTap is designed for an explicit, user-started check.

- The Android app requests Internet only. It does not request SMS, contacts,
  storage, phone, or location permissions.
- A shared image/video Uri is read only after another app gives TrustTap that
  share. It is copied to a temporary upload file and deleted after the request.
- Shared text is sent only when the user selects Share or pastes it. TrustTap
  never silently reads the SMS inbox.
- Links are inspected as strings. TrustTap does not automatically open them,
  follow redirects, log in, send messages, or make payments.
- Detailed reports stored in local History are encrypted with an Android
  Keystore AES key. Older unencrypted history entries remain readable for
  migration compatibility; new entries are encrypted.
- Evidence lookup is best-effort and follows the existing backend's external
  configuration. Do not enable third-party evidence services if the content is
  too sensitive for that service.
- Text and media results are safety signals, not proof. Users should verify
  important claims through a trusted contact or official channel.

The backend currently receives the media/text needed for analysis. A production
deployment should use HTTPS, authentication, retention limits, and a real
attested confidential-computing service before handling sensitive material.
