# Last Rites

Achievement-style death announcements for Old School RuneScape's Hardcore Worlds.

![Sample death announcement](docs/images/death-card.png)

- Pop-ups show the player, final hit, reported killer and region when supplied.
- Missing causes stay unlabelled. The plugin does not guess at weather, agility,
  poison, or other causes from missing information.
- Move with **Alt + drag on Windows** or **Option + drag on Mac**. Hold the same
  key and drag an edge to resize. RuneLite remembers both size and position.
- Scroll over the pop-up to browse up to 50 recent deaths.
- **Show on new deaths** controls whether live events can open it; a separate
  visible/hidden indicator shows its current state. Manually hiding turns that
  preference off. Timer expiry leaves it on. Samples override hiding temporarily.
- Set the duration in seconds: **7 by default**, **0 to stay visible**. Manually
  showing the pop-up pins it until hidden. Each new live death restarts a timed view.
- Full integer broadcast totals are preserved. Abbreviated totals can be enriched
  through Jagex's **Hardcore Main** HiScores table. These values follow the original
  total, for example **Example (1K) - 1075**. The suffix comes from HiScores and
  may lag or reflect a subsequent reset. **Full totals (network lookup)** is off
  by default. Enable it to send broadcast player names to Jagex HiScores; turn it
  off to use the broadcast alone. Unavailable lookups keep the broadcast.
- Regex exclusions can match the original broadcast or named fields. Open the
  skull sidebar to save filters, view recent deaths, preview the pop-up, or find the Plugin Hub issue-reporting instructions.

Version **0.0.1** is the first Plugin Hub submission; it is not available in the
Hub until RuneLite reviews and merges the submission.
It recognises the verified broadcast formats documented in [the implementation
notes](docs/IMPLEMENTATION.md). New Jagex formats are ignored until verified.

## Try it locally

From the parent workspace:

```sh
./plugin check Last-Rites
./plugin gradle Last-Rites build
./plugin run Last-Rites
```

Or independently from this repository: `./gradlew check build` and `./gradlew run`.
Java 11 is the compilation target; development is tested on macOS with JDK 21.
The launcher registers Last Rites with `ExternalPluginManager.loadBuiltin` before
starting RuneLite. It uses RuneLite's existing profiles and credential handling.
No credential file is copied or read by the plugin or workspace tooling.

Enable **Last Rites** in the normal Plugins panel. Click its skull icon, then
**Show sample death** or **Show pop-up**. Live announcements require
a Hardcore world. The sample never makes a HiScores request or enters history.

## Filters

Put one regex per line in **Hide matching deaths**, or use the sidebar editor.
Matching is case insensitive, and a match anywhere excludes that death.

| Example | Excludes |
| --- | --- |
| `\btotal=33\b` | Exactly total 33, not 330 |
| `\btotal=(33\|34)\b` | Total 33 or 34 |
| `killer=.*Goblin` | A reported killer containing Goblin |
| `player=Example Name\b` | A named player |
| `damage=0\b` | A zero-damage final hit |

Named fields are `player`, `total`, `broadcastTotal`, `damage`, `killer`, and
`location`. `total` uses a full HiScores value once available; `broadcastTotal`
always retains the server text. Missing `killer`/`location` values are empty.
Filters apply before lookups and are checked again when a full total arrives.
An announcement already displayed may therefore be removed after enrichment.

Filters use Java's built-in `java.util.regex` API, including lookarounds and
backreferences. Invalid rules are identified by line in the sidebar; valid rules
continue working. Limits: 32 lines, 512 characters per rule.

## Support

To report a bug or request a feature, open **Last Rites** on the **Plugin Hub**
and use **Report an issue**. This opens the plugin's
[GitHub issue page](https://github.com/mataeo-eh/Last-Rites/issues).

The sidebar provides these instructions. Last Rites contains no custom report
form, report transport, or report-draft storage.

## Validation and rollout

Tests cover observed death formats, blank causes, exact/abbreviated totals, regex
rules and errors, queueing/enrichment, rendering, overlay position restoration,
server-channel/world gating, HiScores outcomes, sidebar controls, and Plugin Hub
support instructions. The Plugin Hub uses `build=standard`; production code
requires only the JDK and RuneLite's existing APIs.

Before approval: validate additional live server formats and have testers check
real Hardcore worlds and client sizes. The submission uses the standard Plugin Hub build with no additional runtime
dependencies. See [the release procedure](docs/RELEASING.md).
