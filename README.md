# Arcana Table

Arcana Table is a mobile-first digital tabletop for multiplayer **Magic: The Gathering**, designed with Commander games in mind.

Players can create or join a room, bring in a deck, and manage their game from a shared table on their phones.

## What it does

- Provides multiplayer rooms for local Wi-Fi or internet games.
- Includes a lobby for joining players and getting the table ready.
- Organizes each player's battlefield and game information in a touch-friendly view.
- Provides controls for common game actions and turn flow.

Arcana Table is under active development. Rules automation and individual card interactions continue to evolve; use the table as a companion to your group’s normal rules checks.

## Project site

Visit the [Arcana Table project page](https://aarmou.github.io/arcana_table/).

## Source code

The Android app and internet room service source are in [android/](android/). The stable source is Build 72. The separate `experimental/targeting` branch contains Targeting Experimental Build 78 and uses a different Android package ID so it can be installed beside the stable app. Installable experimental APKs are built by GitHub Actions and attached as workflow artifacts; they are separate from the stable app and Worker.

Open the android folder in Android Studio to build the app. See [android/README.md](android/README.md) for features, requirements, and build notes. The internet room service is in [android/cloud-room/](android/cloud-room/).

## Project page and Pages deployment

- index.html — responsive project site.
- .github/workflows/pages.yml — publishes only the landing page with GitHub Actions when the page or workflow changes on main.

Edit index.html to update the site. The Android source is stored separately under android/ and is not included in the published Pages artifact.

## Disclaimer

Arcana Table is an independent fan-made project and is not affiliated with, endorsed, sponsored, or specifically approved by Wizards of the Coast. Magic: The Gathering is a trademark of Wizards of the Coast LLC.
