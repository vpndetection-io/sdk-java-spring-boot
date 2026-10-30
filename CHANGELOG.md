# Changelog

What each release changed for you, newest first. Each line is a commit's summary, linked to its full description and diff. Releases before 3.0.4 are described by their release commits.

## 3.0.20 - 2026-10-30

### Fixes

- Require vpndetection 6.10.1: a negative retry count per call is refused ([`88a6307`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/88a6307fd2221d855059f7d15f39c34f12221dcd))

## 3.0.19 - 2026-10-26

### Fixes

- Require vpndetection 6.10.0, which reads dates as RFC 3339 alone and refuses a null or an unknown license type ([`4ab4bff`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/4ab4bff9d3efbda71711e225fe86e8583767e871))

## 3.0.18 - 2026-10-24

### Fixes

- Require vpndetection 6.9.1: block conditions match on location and asn ([`65a7980`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/65a79807094c1c02635abea4c124b43dff39ee07))

## 3.0.17 - 2026-10-20

### Features

- Require vpndetection 6.9.0: lookup results carry location() and asn() ([`f3f4d36`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/f3f4d36ae0593eb83d2ef96e2ad6e7ed4963c27e))

## 3.0.16 - 2026-10-20

### Fixes

- Require vpndetection 6.8.0: client_id_issued_at is 64-bit ([`ae0b5c2`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/ae0b5c237dee1ddaa2b392e2656da266f89c7914))

## 3.0.15 - 2026-10-19

### Fixes

- Require vpndetection 6.7.0: num_ports and num_proxies in the pubproxy detail ([`e6967c8`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/e6967c8b0b102cfe3619ae8c98a40c6160d6de75))

## 3.0.14 - 2026-10-18

### Fixes

- Require vpndetection 6.6.0: isPubproxy, and answers read strictly ([`a1ae374`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/a1ae3740873e8af15a3af9dd616df0526ab9c5a0))

## 3.0.13 - 2026-10-17

### Fixes

- Require vpndetection 6.5.0: Retry-After read strictly, the key trimmed ([`97feea2`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/97feea22b279318679d1aaac85aa029c59a3de2a))

## 3.0.12 - 2026-10-16

### Fixes

- Require vpndetection 6.4.2: the spec re-pinned to 2026.10.15 ([`be1906d`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/be1906d04c4f4cd8c4ede2b0f23ca2e3af0f5ac9))

## 3.0.11 - 2026-10-12

### Fixes

- Send no key when the API key variable is unset, and warn at startup ([`2d19103`](https://github.com/vpndetection-io/sdk-java-spring-boot/commit/2d191031b954c6ad3f6afe3606e14ec969b88128))

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
