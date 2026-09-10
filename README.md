# Stream Chat

Shows live **Twitch**, **YouTube** and **Kick** chat in the RuneScape chatbox or in a movable
on-screen panel, with a per-platform icon on every line.

![icons](docs/icons.png)

```
<twitch>  SomeUser: yo that drop is insane
<kick>    Another_1: KEKW
<youtube> ThirdPerson: gz on the pet
```

Read-only. It never sends anything to a stream, never types in game, and never reads your game chat.

---

## Install

**Plugin Hub** (pending [PR #16360](https://github.com/runelite/plugin-hub/pull/16360)): RuneLite &rarr; wrench icon &rarr; *Plugin Hub* &rarr; search "Stream Chat".

**Local build:**

```sh
./gradlew build          # compile + tests
./gradlew runClient      # launch RuneLite with the plugin loaded
```

Requires a JDK to build. Java 11 is the target; any JDK 17+ can build it (`options.release = 11`).

---

## Setup

Everything lives under the plugin's config panel, grouped by platform.

### Twitch

1. Tick **Enable Twitch**.
2. Put channel names in **Channels**, comma separated: `odablock, settled`.

That is the whole setup. **No account, login or token is needed.** Twitch accepts anonymous
read-only IRC connections, and still sends display names and name colours, so a token would add
nothing to a read-only feed. Pasted URLs and leading `#`/`@` are tolerated.

### Kick

1. Tick **Enable Kick**.
2. Put channel slugs in **Channels**: `trainwreckstv, spreen`.

Also no account or token needed.

<details>
<summary>If Kick chat never connects (&ldquo;could not resolve chatroom id&rdquo;)</summary>

Kick's site API sits behind Cloudflare, which sometimes refuses non-browser clients. The chatroom
ID is stable per channel, so you can supply it by hand:

1. Open `https://kick.com/api/v2/channels/<slug>` in your browser.
2. Find `"chatroom": { "id": 123456, ... }`.
3. Put `slug:123456` in **Chatroom IDs**, comma separated for several channels.

Channels listed there skip the lookup entirely.
</details>

### YouTube

YouTube is the one platform that needs a credential, because Google requires one for *all* Data API
calls. You supply your own key, so the quota consumed is yours.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/), create a project.
2. **APIs & Services &rarr; Library &rarr; YouTube Data API v3 &rarr; Enable**.
3. **APIs & Services &rarr; Credentials &rarr; Create credentials &rarr; API key**.
4. Restrict the key to *YouTube Data API v3* (recommended).
5. Paste it into **API key**, and put the live video URL into **Video or channel**.

**Paste the live video URL, not the channel.** This matters a lot:

| Target | API call | Quota cost |
| --- | --- | --- |
| Video URL or ID | `videos.list` | **1 unit** |
| Channel ID or `@handle` | `search.list` + `videos.list` | **101 units** |

A Google project gets **10,000 units/day**. Reading messages costs 5 units per poll, so at the
default 5-second interval you get roughly **2.5 hours of viewing per day**. Raise **Minimum poll
interval** to stretch that further.

If you use a channel/handle target, keep **Offline re-check** high &mdash; every check while the
channel is offline burns another 100 units.

OAuth is deliberately not used: it is only required to read *private/unlisted* broadcasts or to post
messages, and this plugin does neither.

---

## Display

| Option | Notes |
| --- | --- |
| **Display** | Chatbox, on-screen panel, or both. |
| **Chatbox tab** | Which tab to print to: Game, Channel, Clan or Trade. Channel/Clan keeps stream chat out of your game messages. |
| **Source icon** | The per-platform icon prefix. |
| **Show channel name** | Useful when watching several channels at once. |
| **Colour author names** | Uses the chatter's own colour where the platform sends one. Very dark names are brightened so they stay readable. |
| **Max message length** | Long messages are truncated. |

## On-screen panel

A movable, resizable panel, as an alternative to the chatbox. Drag it anywhere; **drag its edge to
set a height** and it fills with as many recent messages as fit, newest at the bottom, like a chat
window. Position and size persist.

| Option | Notes |
| --- | --- |
| **Max messages** | How many to keep. A fixed height shows as many of these as fit. |
| **Width** | Starting width. Dragging overrides it. |
| **Background** | Transparent by default. Raise the alpha for a solid panel. |
| **Message text** | Body colour. Author names use the platform/chatter colour. |
| **Font** | Client default, small, regular or bold. |
| **Text shadow** | On by default, and worth keeping while the background is transparent. |
| **Border** | Thin outline. |

## Events

Subscriptions, gifted subs, raids, cheers and Super Chats are detected on all three platforms:

| Platform | Detected from |
| --- | --- |
| Twitch | `USERNOTICE` (subs, resubs, prime upgrades, gifts, mystery gifts, raids) plus the `bits` tag for cheers |
| Kick | `SubscriptionEvent`, `GiftedSubscriptionsEvent`, `StreamHostEvent` |
| YouTube | memberships, member milestones, gift memberships, Super Chats and Super Stickers |

| Option | Notes |
| --- | --- |
| **Show events** | Show them alongside chat. |
| **Highlight events** / **Event colour** | Draw event lines in their own colour. |
| **React to** | Every event, subs + gifts + raids, or subs only. |
| **Sound** | A game sound effect. |
| **Notification** | A RuneLite notification, following your notification settings. |
| **Play graphic** | Level-up, **99** or max-cape fireworks on your character. |
| **Play emote** | An emote animation, when you are idle. |

> **These reactions send nothing to the game.** The sound plays locally, the notification is a
> desktop one, and the graphic and emote are written onto your character's *rendering* only -- no
> packet, nothing server-side, and no other player sees them. Automating a real emote would be input
> simulation, which breaks Jagex's third-party client rules; this deliberately is not that.

## Filters

| Option | Notes |
| --- | --- |
| **Messages per tick** | Print budget per 0.6s tick. The main flood control. |
| **Queue size** | When full, the **oldest** messages are dropped so the chatbox stays live. |
| **Note dropped messages** | An occasional `(n messages skipped)` line. |
| **Hide bot commands** | Hides messages starting with `!` or `?`. |
| **Hide emote-only messages** | Hides messages with no words of their own. |
| **Blocked users / words** | Comma separated, case insensitive. |

Emote-only detection uses what each platform reports -- Twitch's `emotes` tag character ranges,
Kick's `[emote:id:name]` markup, YouTube's `:shortcode:` tokens -- rather than guessing from the
text, so it cannot swallow a real message. BTTV/FFZ/7TV emotes are *not* detected: they never appear
in Twitch's emote tag, and identifying them would mean downloading those services' emote lists,
which is exactly what the Plugin Hub rejects emote plugins for.

Type `::streamchat` in game to print the connection status of each platform.

---

## How it handles a busy stream

A popular channel can produce far more messages per second than a chatbox can show. Rather than
letting that push your game messages out of view, incoming messages go through a bounded queue and a
per-tick print budget. When the queue overflows the **oldest** entries are discarded, so what you see
stays close to live, and an occasional `(n messages skipped)` line makes the gap visible rather than
silent.

Messages also expire after 30 seconds. Game ticks only fire while you are logged in, so nothing is
printed at the login screen, during a world hop, or on a disconnect -- while the feeds keep
receiving. Without that cap, everything buffered during those gaps would arrive in one burst the
moment ticks resumed.

Reconnection uses exponential backoff capped at five minutes, with jitter so several channels do not
all reconnect in lockstep after an outage. Errors that retrying cannot fix &mdash; a rejected API
key, a suspended channel, exhausted quota &mdash; stop cleanly and say why in `::streamchat` instead
of hammering the service.

## Safety

Stream chat is untrusted text from strangers, and the game chatbox treats `<...>` and `@` as markup.
Left unescaped, a chatter could inject `<img=n>` to fake a mod crown, `<col=...>` to recolour the
line, or `@...@` colour codes.

Every message goes through `ChatText.clean` (collapse whitespace, drop characters the game font
cannot render, truncate) and then RuneLite's `Text.escapeJagex` before it reaches the chatbox.
Truncation happens *before* escaping so a cut can never land inside a generated `<lt>`. This is
covered by tests in `ChatTextTest`.

## Privacy

- No game data leaves your client. The plugin reads nothing from your account and sends nothing to
  any stream.
- Network connections are only to `irc-ws.chat.twitch.tv`, `ws-us2.pusher.com`, `kick.com` and
  `googleapis.com`.
- Your YouTube API key is sent only to `googleapis.com`. It is stored in plain text in your
  RuneLite profile, like every other RuneLite setting -- the config field masks it on screen but
  does not encrypt it, and it syncs to RuneLite's servers if you use a RuneLite account. Restrict
  the key to the YouTube Data API v3 so a leak is limited to read-only quota use.
- No credentials are bundled with the plugin.

---

## Development

```sh
./gradlew build          # compile + tests
./gradlew runClient      # RuneLite with the plugin as a builtin
python3 tools/make_icons.py   # regenerate the chat icons
```

The plugin has **no third-party dependencies** &mdash; OkHttp (including its WebSocket client), Gson,
Guava and Guice all come from the RuneLite classpath. This is deliberate: Plugin Hub requires
dependency verification hashes for any added dependency, which significantly extends review.

Icons are 11x11 PNGs. RuneLite's sprite converter draws a pixel **only if it is fully opaque**, so
they are hand-placed pixel art with no antialiasing &mdash; see `tools/make_icons.py`.

### Layout

| Path | Role |
| --- | --- |
| `StreamChatPlugin` | Lifecycle, config wiring, `::streamchat` |
| `MessageRouter` | Filtering, de-duplication, rate limiting, rendering |
| `ChatText` | Sanitising. The security boundary. |
| `ChatIcons` | Registers the source icons, resolves `<img=n>` indices |
| `StreamChatOverlay` | The on-screen panel: layout, wrapping, styling |
| `EventReactions` | Client-side reactions to events (sound, notification, graphic, emote) |
| `AbstractChatSource` | Lifecycle, status, reconnect backoff shared by all sources |
| `twitch/` | IRC-over-WebSocket client + IRCv3 parser |
| `youtube/` | Data API v3 polling + target parsing |
| `kick/` | Pusher chatroom socket |

## Status

Submitted to the RuneLite Plugin Hub: [runelite/plugin-hub#16360](https://github.com/runelite/plugin-hub/pull/16360).

Until it is merged, install by building locally (`./gradlew build`) and running `./gradlew runClient`,
or by dropping the built jar from `build/libs/` into `~/.runelite/plugins/`.
