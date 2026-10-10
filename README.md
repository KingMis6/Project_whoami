# Who Am I? (Android, Kotlin, XML views, Firebase live sync)

Pastel party game for 2–6 players, each on their own phone. Everyone types in a
character (real person / cartoon / anime), the app deals each player a character
someone ELSE wrote, and you guess yours by asking yes/no questions out loud.

## Game flow
1. **Home**: enter your name, create a room or join with a 5-letter code.
2. **Lobby**: live player list, host taps Start (needs 2–6 players).
3. **Submit**: everyone privately types a character. A picture is looked up
   automatically (Wikipedia, best effort).
4. **Deal**: when the last player submits, the host's phone shuffles and deals
   (nobody gets their own). Everyone sees everyone else's card; yours shows "?".
5. **Play**: turns rotate. On your turn, ask questions out loud, then **Make a
   guess** (right = solved, wrong = turn passes) or **Pass turn**.
6. **Notes**: collapsible sticky-note panel on the game screen. Private,
   stored only on your phone.
7. Game ends when everyone has solved theirs.

## Setup (one time)
1. Firebase Console → **Add project**.
2. **Build → Realtime Database → Create database** (do this BEFORE step 4, so
   the config file includes your database URL).
3. **Build → Authentication → Sign-in method → Anonymous → Enable**.
4. **Project settings → Add Android app**, package `com.example.whoami`
   (or change `namespace`/`applicationId` in `app/build.gradle`), download
   `google-services.json` and put it in `app/`.
5. **Realtime Database → Rules**, paste:

```json
{
  "rules": {
    "rooms": {
      "$roomCode": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    },
    "submissions": {
      "$roomCode": {
        ".read": "auth != null && auth.uid == root.child('rooms').child($roomCode).child('hostUid').val()",
        ".write": "auth != null && auth.uid == root.child('rooms').child($roomCode).child('hostUid').val()",
        "$uid": {
          ".write": "auth != null && auth.uid == $uid"
        }
      }
    }
  }
}
```

Only the host's device can read submissions (it needs them to deal). Note that
`rooms/*/assignments` is readable by every player, because everyone else has to
see your card. Hiding yours from you is done in the UI, which is fine for a
friends' party game but not tamper-proof.

6. Open the folder in Android Studio, sync, run.

**Testing alone:** run two emulators (or emulator + real phone). Each install
gets its own anonymous user, so you can play against yourself.

## Project map
- `data/model`: `Player`, `Character`, `GameRoom`, `RoomStatus`
- `data/repository/GameRepository.kt`: every networked action
- `data/repository/ImageLookupService.kt`: Wikipedia picture lookup
- `data/local/NotesManager.kt`: private notes (SharedPreferences)
- `ui/room/RoomViewModel.kt`: watches one room live; shared by Lobby/Submit/Game
- `ui/home|lobby|submit|game`: the four screens
- `util/GuessMatcher.kt`: forgiving guess checking (punctuation, spacing, 1 typo,
  "Goku" vs "Son Goku"). Tune thresholds there.
- `res/values/colors.xml` + `themes.xml`: the pastel palette and component styles

## Known limitations / good next steps
- Pictures are best effort from English Wikipedia; made-up characters get a
  placeholder. Add your contact email to the User-Agent in `ImageLookupService`.
- No "rejoin my game after killing the app" yet.
- Abandoned rooms (everyone force-quits) linger in the database; add a small
  cleanup later (Cloud Function or a manual purge).
- UI strings are inline in layouts; extract to `strings.xml` (Alt+Enter) if you
  want translations.
- Launcher icon is a simple vector; replace via Image Asset Studio.
