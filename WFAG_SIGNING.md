# WFAG stable signing

- Application ID: `mk.workfashion.personal.stable`
- First version: 3.6.0 (versionCode 29), 26 November 2026.
- Certificate SHA-256: `856a22b1c9514f56ca4f41b93c425da7989cdf4c5cd00aa97ca1d4baacd2a20f`
- Alias: `wfag-stable`; keystore type: PKCS12.

The private signing recovery bundle is retained privately for the owner as `WFAG_STABLE_SIGNING_PRIVATE.zip`. Never commit the private key or its password to this public repository. Sign every future stable update with this exact certificate.

The previously supplied 3.4.1 ROBA APK uses application ID `mk.workfashion.personal.pickups34` and a different temporary Android Debug certificate. The stable package installs alongside it. Transfer records and document files through a complete ZIP backup while keeping the old application available.

The workflow builds and verifies an unsigned stable APK. Signing happens separately; an unsigned artifact is not an installable deliverable.
