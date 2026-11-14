# Implementation and research notes

## Existing plugins checked on 13 November 2026

The full, non-truncated [Plugin Hub registry](https://github.com/runelite/plugin-hub/tree/master/plugins)
was checked along with the public Hub and web search. No matching movable
Hardcore death achievement pop-up was found. This cannot rule out unpublished work.

- [Hardcore World Tool Kit](https://github.com/Novafifth/hardcore-world-tool-kit)
  reads death broadcasts for collection/filtering; its public death tracker is
  related but does not implement this achievement-style card.
- [Death Message Tracker](https://github.com/soulkraken/death-message-tracker)
  aggregates statistics in a sidebar.
- [Death Message](https://github.com/Romansolja/death-message) displays a custom
  overhead message on the local player's death.

## Architecture

`LastRitesPlugin` gates events on `WorldType.HARDCORE_MAIN` and server message
types `BROADCAST`/`GAMEMESSAGE`. `BroadcastParser` accepts only verified death
grammars. `DeathNotice` preserves source precision. `NoticeFilters` compiles user
exclusions with `java.util.regex.Pattern`. `NoticeQueue` synchronizes the bounded display queue and history.
`LastRitesOverlay` paints the card and delegates position persistence to RuneLite.
`LastRitesPanel` exposes preview, filters, history, and instructions for the
Plugin Hub's existing Report an issue link.

The display stores 50 session history records, never written to disk. New deaths
appear immediately and restart the timed view; scrolling browses the same history.
The timer defaults to 7 seconds, permits 0 for persistent display, and is separate
from manual pin/hide. A manually hidden popup stays hidden on live events; a
sample overrides that without changing the saved preference. History remains until shutdown.

## Exact API contracts

Verified against RuneLite source commit
`bb0fa42b3d9329ffe31115f1f20042c455def2e3` and the installed release's compilation.
Context7 was consulted before implementation; the source provides the precise
current contracts not covered by its indexed examples.

[`ChatMessage`](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/events/ChatMessage.java)
returns `messageNode`, `type`, `name`, `message`, `sender`, and `timestamp`.
Only `type` and `message` are consumed. `name`/`sender` are chat metadata,
`timestamp` is not a character's death timestamp, and `messageNode` does not
provide separate total/killer fields. Those four fields are intentionally ignored.

Verified formats from the toolkit's tests/source at
`f0476c89bb385f7666614c9a695ed4f71c3b0b30`:

```text
Damis killed HCW4(1K) with a 24 damage hit in Kandarin.
Pk tester(120) killed Seed Player0(70) with a 25 damage hit in the Wilderness.
Made Up2(1306) was killed by a 12 damage hit from a Hill Giant.
Low Lvl(42) was killed by a 3 damage hit from a Goblin.
```

The no-named-killer form is handled with an empty cause. The parser allows a
missing region and ordinary game formatting via RuneLite's `Text` accessors.
Malformed or unfamiliar text is ignored. No environmental cause is inferred.

An integer total remains exact as reported. A `K` suffix remains abbreviated;
the plugin does not multiply and present that as an exact total.

[`HiscoreClient.lookupAsync`](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/hiscore/HiscoreClient.java)
returns `CompletableFuture<HiscoreResult>`; 404 gives null, request failures may
complete exceptionally, and other uncompleted error paths are bounded by an
8-second future timeout. The dedicated `HiscoreEndpoint.HARDCORE_MAIN` uses
Jagex's `hiscore_oldschool_hardcore_mains/index_lite.json` endpoint. Only
`HiscoreResult.getSkill(OVERALL).level` is used; absent skill/result and
nonpositive levels are ignored. The `player` field, rank, XP, other skills and
activities are ignored. No table is substituted after failure. Disabling
lookups clears pending work and suppresses in-flight results.

One result is awaited at a time, with at most one new start each second and 25
pending names. The future timeout ends waiting; RuneLite does not expose the HTTP
Call for cancellation, so timed-out transport may still finish in the background.
All plugin-requested networking is behind the full-total lookup option, which
is off by default. Only abbreviated broadcast totals trigger lookups after opt-in. The returned number is
shown after the original abbreviation, for example `(1K) - 1075`. A later profile
reset cannot be distinguished reliably from the death-time character using this
API, so this is a full published total, not proof of an exact death-time total.

## Remaining implementation/rollout work

1. Validate newly encountered live server fixtures; expand the strict parser only
   with evidence. A future structured RuneLite death event should replace it.
2. Have live testers validate fixed/resizable sizes and UI scaling. Manager
   recreation tests cover saved position and size; wheel-event tests cover history
   browsing, precise trackpad rotation, and leaving camera scrolling untouched outside the popup.
3. Observe service update/reset behaviour before making stronger death-time
   precision claims. Preserve the source labels even if a lookup succeeds.
4. Follow docs/RELEASING.md for the standard Plugin Hub submission. Only the
   plugin manifest needs changing; there is no additional runtime dependency.
5. Bug reporting uses the Plugin Hub issue link. The reusable native reporting
   template is local workspace tooling under shared/templates/native-reporting,
   independent of Last Rites and IRL XP's existing implementation.

## Development launch constraint

RuneLite enables developer mode only when `--developer-mode` is present and
`RuneLiteProperties.getLauncherVersion()` is null. The installed launcher thus
does not load sideload JARs even when passed that flag. Use the repository's
`run` task, which follows the official example's `loadBuiltin` registration. The
`sideloadJar` artifact is available for an independently started developer client;
do not install it while also registering the same plugin through the launcher.
