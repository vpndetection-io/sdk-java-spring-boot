# [<img src="https://s3.vpndetection.io/vpndetection-public/brand/mark.svg" alt="VPNDetection" width="24"/>](https://vpndetection.io/) VPNDetection Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/io.vpndetection/vpndetection-spring-boot-starter.svg)](https://central.sonatype.com/artifact/io.vpndetection/vpndetection-spring-boot-starter)
[![license](https://img.shields.io/github/license/vpndetection-io/sdk-java-spring-boot.svg)](LICENSE)

The official Spring Boot starter for the [VPNDetection](https://vpndetection.io) API.

It classifies the visitor behind each request — VPN, residential proxy, Tor, hosting, CDN, relay — and hands the answer to your controllers. Blocking is opt-in.

## Getting Started

```xml
<dependency>
    <groupId>io.vpndetection</groupId>
    <artifactId>vpndetection-spring-boot-starter</artifactId>
    <version>3.0.1</version>
</dependency>
```

Requires Java 17 or newer and Spring Boot 3.

You need an API key. Create one in the [console](https://app.vpndetection.io); the free tier's allowance is counted per source address, and a server is a single source address, so a key is what makes this usable in production rather than optional.

```yaml
# application.yml
vpndetection:
  api-key: ${VPNDETECTION_API_KEY}
```

The filter auto-configures itself. Read the answer anywhere downstream:

```java
@GetMapping("/")
String index(HttpServletRequest request) {
    return VPNDetectionFilter.lookup(request)
            .flatMap(Lookup::result)
            .filter(Result::isVpn)
            .map(r -> "Hello, VPN user")
            .orElse("Hello");
}
```

By default nothing is blocked. Every request gets an answer and your own code decides what that means — which is usually what you want, because whether a VPN visitor is a problem depends entirely on what they are doing.

## Blocking

Set a `block-condition` and a matching request is answered with `403` and never reaches your controllers.

```yaml
vpndetection:
  api-key: ${VPNDETECTION_API_KEY}
  block-condition:
    - is_vpn: true
```

A condition is written in the shape of a served answer, keyed by the same names the API uses, and only the members you name are considered. That lets it reach the evidence, not just the flags:

```yaml
block-condition:
  - is_vpn: true
    vpn: { provider: nordvpn }        # one provider
  - is_resproxy: true
    resproxy: { hits: { gte: 5 } }    # a numeric threshold
```

The list is OR: any one entry blocking is enough. Values are matched by equality, strings without regard to case. A list *value* means any-of. A map of `gte`/`gt`/`lte`/`lt` compares numbers, and every bound you give must hold, so two of them are a range. Members set to `false` or `null` are ignored, so a condition states the signals you act on; one that constrains nothing would match every request, and is refused when the context starts rather than silently blocking all your traffic.

In Java, build conditions with `Bound.gte(5).andLt(100)` and define your own `Core` bean — a typo in a bound is then a compile error rather than a silently ignored key.

Replace the refusal by defining your own `VPNDetectionFilter` bean; the auto-configuration backs off to it.

## Where the client address comes from

This is the setting that decides whether any of the above works, and it is the one thing only you can get right.

By default the filter uses `HttpServletRequest#getRemoteAddr`, the socket peer. **The servlet API has no trusted-proxy setting of its own.** Behind nginx, a load balancer, or a CDN, every visitor arrives wearing your proxy's address — which is a datacenter address, so a hosting rule would block all of them.

Spring Boot's own answer is the one to reach for first, because it rewrites `getRemoteAddr` before this filter ever runs:

```yaml
server:
  forward-headers-strategy: framework   # or native, behind a container that does it for you
```

For an edge that writes the address into its own header, name it:

```yaml
vpndetection:
  client-ip-header: CF-Connecting-IP
```

A callable cannot live in YAML, so anything more exotic — a chain depth, your own logic — means defining a `Function<HttpServletRequest, String>` bean named `vpndetectionIpSelector`. Every bean here is `@ConditionalOnMissingBean`, so yours replaces ours rather than colliding with it.

If the address resolves to a private one, the filter says so once. That is expected locally and is the signal to fix your configuration anywhere else.

## When a lookup fails

The request is let through, and the reason is on `lookup.error()`. Our outage should not become yours, so a network failure, an exhausted quota or a rejected key all fail open. Set `fail-closed: true` to block instead. Private addresses are answered locally and never fail, so this will not lock you out in development.

## Cost and latency

Answers are cached for an hour, so a returning visitor costs nothing, and private addresses never leave the process. A cache miss is one request to our API, bounded at 2500 ms by default and not retried — on a request path, failing open quickly beats holding a visitor while we try again.

Skip what you do not care about:

```yaml
vpndetection:
  skip-paths: ["/actuator/**", "/static/**"]
```

Beyond a few million distinct visitors a day, stop calling the API per request: [download the dataset](https://vpndetection.io/databases) and look addresses up locally instead.

## Absent is not false

Only `ip` and `isVpn` come back on every plan. A member your plan does not include is an empty `Optional`, which means "not in your plan" rather than "checked, and no".

```java
result.isHosting().orElse(false)   // when you only want the flag
```

A `block-condition` naming a member your plan does not serve can never match, so the filter warns once instead of failing silently. Set `on-missing-field: throw` to make it an error.

## Other Libraries

There are official VPNDetection client libraries available for many languages including PHP, Python, Go, Java, Ruby, and many popular frameworks such as Django, Rails, and Laravel. See our GitHub at https://github.com/vpndetection-io for more.

## About VPNDetection

VPN Detection API: Accurate anonymity detection identifying VPNs, residential proxies, hosting servers, Tor nodes, CDNs, relays and more.

[<img src="https://s3.vpndetection.io/vpndetection-public/brand/mark.svg" alt="VPNDetection" width="96"/>](https://vpndetection.io/)

## License

This project is licensed under the [MIT License](LICENSE).
