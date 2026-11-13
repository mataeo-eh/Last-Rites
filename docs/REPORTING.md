# Reusing native plugin reporting

`src/main/java/com/runeliteplugins/reporting` is the portable source package
extracted from IRL XP. It owns report data, validation, native UI, drafts,
confirmation, transport, and strict receipt checking. `ReportingSettings` supplies
the display name, single-component draft directory, and trusted public endpoint.
No production code imports IRL XP or refers to the parent workspace.

A plugin supplies a Guice `@Provides ReportingSettings` method, injects
`BugReportController`, calls `open(owner)` on the Swing EDT from its report button,
and calls `shutDown()` on the EDT when it stops. Supply unique display/draft names.
Report titles automatically receive `[Display name]` and the preview shows that
exact title. The receiver's schema remains unchanged: `category=OTHER` for the
portable form. This shares the existing private inbox without deploying a new
backend or exposing its destination. Future dedicated routing needs a versioned
receiver contract rather than an unrecognised client-only field.

The receiver expects schema 1 and a canonical UUID v4 `submissionId`, category,
title (120 characters), description (4000), steps (4000), expectedResult (2000),
and optional diagnostics with exactly `featureRevision=native-reporting-v1`,
`osName`, `javaVersion`, and `runeLiteVersion` (100 characters each). Body limit
is 49152 UTF-8 bytes. Unknown payload fields are rejected. The title prefix is
included in the 120-character title limit. Empty drafts can be saved.

HTTP 202 is accepted only when the bounded JSON receipt echoes the same UUID in
`submissionId` and `reference` and has `status=accepted`. Extra response properties
are ignored; duplicate/mistyped required properties, malformed JSON and unexpected
status codes never claim acceptance. 400/413/415 mean invalid, 409 conflict,
429 limited, and 503 unavailable. Network/timeout failures mean unknown acceptance.
Redirects and automatic retries are disabled. An unchanged retry preserves its ID.
Sending saves an atomic recovery draft; only a matching receipt clears it.

From the parent workspace, `shared/tools/vendor-reporting.py TARGET_REPO` copies
this package and its tests into a new plugin and writes a SHA-256 provenance
manifest. Independently, run `python3 tools/vendor_reporting.py TARGET_REPO` from
this repository. It refuses to overwrite an existing package. The copied plugin remains
standalone. Existing plugins should be migrated deliberately with their own
regression checks rather than overwritten by this scaffolder.

Dependencies come from RuneLite's runtime: Guice, Gson, OkHttp and Swing.
Tests additionally use JUnit Jupiter and Mockito. The parent helper is local
scaffolding only; this repository's ordinary Gradle build does not invoke it.

No live report was sent during development. Tests intercept transport locally;
the real receiver was checked through read-only `/healthz`.
