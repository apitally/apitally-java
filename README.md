<p align="center">
  <a href="https://apitally.io" target="_blank">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://assets.apitally.io/logos/logo-vertical-dark.png">
      <source media="(prefers-color-scheme: light)" srcset="https://assets.apitally.io/logos/logo-vertical-light.png">
      <img alt="Apitally logo" src="https://assets.apitally.io/logos/logo-vertical-light.png" width="150">
    </picture>
  </a>
</p>

<p align="center"><b>Simple, privacy-focused API monitoring & analytics</b></p>

<p align="center"><i>Apitally helps you understand how your APIs are being used and alerts you when things go wrong.<br>Just add two lines of code to your project to get started.</i></p>
<br>

![Apitally screenshots](https://assets.apitally.io/screenshots/overview.png)

---

# Apitally client library for Java

[![Tests](https://github.com/apitally/apitally-java/actions/workflows/tests.yaml/badge.svg?event=push)](https://github.com/apitally/apitally-java/actions)
[![Codecov](https://codecov.io/gh/apitally/apitally-java/graph/badge.svg?token=sV0D4JeWG6)](https://codecov.io/gh/apitally/apitally-java)

This client library for Apitally currently supports the following Java web
frameworks:

- [Spring Boot](https://docs.apitally.io/frameworks/spring-boot)

Learn more about Apitally on our 🌎 [website](https://apitally.io) or check out
the 📚 [documentation](https://docs.apitally.io).

## Key features

### API analytics

Track traffic, error and performance metrics for your API, each endpoint and
individual API consumers, allowing you to make informed, data-driven engineering
and product decisions.

### Error tracking

Understand which validation rules in your endpoints cause client errors. Capture
error details and stack traces for 500 error responses, and have them linked to
Sentry issues automatically.

### Request logging

Drill down from insights to individual requests or use powerful filtering to
understand how consumers have interacted with your API. Configure exactly what
is included in the logs to meet your requirements.

### API monitoring & alerting

Get notified immediately if something isn't right using custom alerts, synthetic
uptime checks and heartbeat monitoring. Notifications can be delivered via
email, Slack or Microsoft Teams.

## Install

Add the following dependency to your `pom.xml` file:

```xml
<dependency>
  <groupId>com.apitally</groupId>
  <artifactId>apitally-java</artifactId>
  <version>[0.1.0,)</version>
</dependency>
```

## Usage

Add Apitally to your Spring Boot application using the `@UseApitally`
annotation.

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.apitally.spring.UseApitally;

@UseApitally
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Then add the following properties to your `application.yml` file:

```yaml
apitally:
  client-id: "your-client-id"
  env: "dev" # or "prod" etc.
```

For further instructions, see our
[setup guide for Spring Boot](https://docs.apitally.io/frameworks/spring-boot).

## Getting help

If you need help please
[create a new discussion](https://github.com/orgs/apitally/discussions/categories/q-a)
on GitHub or
[join our Slack workspace](https://join.slack.com/t/apitally-community/shared_invite/zt-2b3xxqhdu-9RMq2HyZbR79wtzNLoGHrg).

## License

This library is licensed under the terms of the MIT license.
