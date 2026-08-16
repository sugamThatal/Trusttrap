# TEE status and integration boundary

There is no backend inference TEE enabled in this ZIP. A normal FastAPI process
on Windows, an Android emulator, or an ordinary Android app cannot honestly
claim that its model inference is running inside a Trusted Execution
Environment.

The Android app does use Android Keystore for local detailed-history
encryption. Where the phone supports it, the app reports that the key is
hardware-backed. That protects saved reports on the phone; it is not the same
as running the backend model inside a TEE.

To activate this in production, choose and provision a concrete target such as
Android hardware-backed Keystore/KeyMint for local key protection, or a
confidential VM/service using AMD SEV-SNP, Intel TDX/SGX, or a cloud attestation
service. The deployment then needs remote attestation, a release measurement,
key release policy, logging, and a way for the client to verify the attestation.

The current app deliberately does not display a fake “TEE protected” badge.
Text review is sent to the configured backend over the same connection as media.
The next safe implementation step is to place the backend behind a real
attested confidential service, then add certificate/attestation verification to
the Android client.
