# Releasing Last Rites

The first Plugin Hub submission is **0.0.1**. Local prototype version 0.1.0 was
never submitted. Do not apply IRL XP's version preparation script to this repo.

1. Run `./plugin gradle Last-Rites check build` from the parent workspace, or
   `./gradlew check build` independently. Review the rendered card and sidebar.
   Scan production code against RuneLite's current disallowed API list.
2. Pull first, security-scan changed files, commit with the selected version in
   `runelite-plugin.properties`, and push the source repository's `main` branch.
   The source repository must be public for review and builds.
3. Use an isolated checkout of the existing `mataeo-eh/plugin-hub` fork based on
   current `runelite/plugin-hub` master. Add `plugins/last-rites` containing exactly
   the public source repository URL and its full 40-character commit hash.
4. Keep `build=standard`. All production dependencies come from RuneLite or the
   JDK, including `java.util.regex`; no verification-template changes are needed.
5. Push a dedicated fork branch and open one PR titled **Add Last Rites 0.0.1**.
   Describe popup behavior, regex filtering, optional Hardcore Main HiScores
   requests, Plugin Hub issue-reporting guidance, checks, and live-testing limits.
6. Watch both build and Plugin Hub review checks. Fix issues on the same PR and
   update its manifest commit. A PR is a submission, not approval or availability.

Only the plugin source becomes public. Drafts, credentials, logs and the private
report inbox remain outside this repository. The Java source targets release 11.

For later releases, select a new version deliberately, rerun checks, and update
the existing Hub PR if it is still open rather than creating duplicates.

When the user requests approval before publishing review fixes, prepare and test
the changes locally and draft the reply first. Commit, push, update the manifest,
edit the PR description, and post the reply only after that approval.
