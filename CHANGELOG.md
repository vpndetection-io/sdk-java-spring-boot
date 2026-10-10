# Changelog

What each release changed for you, newest first. Each line is a commit's summary, linked to its full description and diff. Releases before 3.0.4 are described by their release commits.

## 3.0.10 - 2026-10-10

### Fixes

- Require vpndetection 6.4.1: the spec re-pinned to 2026.10.09 ([`a362f0b`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/a362f0b5d747fbbabf7e2a975aa43b926e7b0cdc))

## 3.0.9 - 2026-10-04

### Fixes

- Require vpndetection 6.4.0: a huge Retry-After no longer holds a call ([`a0eb14d`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/a0eb14d654ab5833526a7af468b103ea29470fcc))

## 3.0.8 - 2026-10-01

### Fixes

- Require vpndetection 6.3.3 and jackson 2.22.3, past two denial-of-service advisories ([`a721c6a`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/a721c6a951ca419224e0d27c4aa0c5248519d5a4))

## 3.0.7 - 2026-09-30

### Fixes

- Require vpndetection 6.3.2: concurrent requests from one visitor share one lookup ([`6a33d57`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/6a33d57b208bba01cf6badd3d54c21754e504292))

## 3.0.6 - 2026-09-30

### Fixes

- Require vpndetection 6.3.1: an IPv4-mapped visitor is looked up as the IPv4 address it carries ([`b94a06f`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/b94a06f9cbbd9960fb9d56eb1c979d19fe845d07))

## 3.0.5 - 2026-09-27

### Features

- Require vpndetection 6.3.0: OauthMetadata carries clientIdMetadataDocumentSupported ([`c911961`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/c911961f422a60c2c1879596fd442d6dafaadcf3))

## 3.0.4 - 2026-09-22

### Fixes

- Raise the base floor to vpndetection 6.2.2 ([`3664399`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/3664399d8720d1270d77deebee096a0bd3693c1d))
