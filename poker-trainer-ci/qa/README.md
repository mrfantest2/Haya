# Poker Vision QA signing

`poker-vision-qa.jks` is a deliberately non-production signing identity used only for the QA/sideload variant.

- QA application ID: `com.fantest.pokervision.qa`
- Production application ID: `com.fantest.pokervision`
- QA certificate SHA-256: `1D:C1:11:84:9C:13:61:9F:F7:EA:06:59:E2:93:B9:07:99:35:FB:A0:71:83:56:82:35:7F:D8:41:17:B6:07:10`
- The QA key is public and MUST NOT sign a Google Play production release.

The separate QA application ID prevents signature collisions with existing Play Store or older sideload installs. Production releases must use the private Google Play/upload signing identity via a protected release pipeline.
