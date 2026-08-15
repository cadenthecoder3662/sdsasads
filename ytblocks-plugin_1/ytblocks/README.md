# YTBlocks

A Minecraft (Paper) plugin that carves blocks out of a 500x500 play area as
your YouTube video's views/likes grow and your channel gains subscribers.

- **+1 view** = 1 block removed
- **+1 like** = 10 blocks removed
- **+1 subscriber** = 50 blocks removed

(All configurable in `config.yml`.)

It checks YouTube every 60 seconds (configurable), remembers where it left
off between server restarts, and only ever touches blocks inside the play
area you define — never anything else in your world. On startup it also
automatically sets the in-game world border to match that same area, so
players can't wander outside it - no manual commands needed. No pre-built
floor or structure is required; it works on whatever terrain is already
there, carving from the top of each column downward.

---

## How to build this into an installable plugin

This project can't be compiled in this delivery — it needs to be built with
Maven against the real PaperMC API, which requires internet access to
PaperMC's servers. The easiest free way to do that without installing
anything yourself is **GitHub Actions**, which is already set up in this
project (see `.github/workflows/build.yml`). Here's the process:

1. **Create a free GitHub account** at [github.com](https://github.com) if
   you don't already have one.
2. Click the **+** icon (top right) → **New repository**. Give it any name
   (e.g. `ytblocks-plugin`), keep it Public or Private (either works), and
   click **Create repository**.
3. On the new repo's page, click **uploading an existing file** (or
   **Add file → Upload files**).
4. Drag this entire folder's contents into the upload box — including the
   hidden `.github` folder. If your browser only lets you drag files (not
   folders), drag each file/folder in one at a time, keeping the same
   folder structure shown here:
   ```
   pom.xml
   README.md
   src/main/java/com/caden/ytblocks/YTBlocksPlugin.java
   src/main/resources/plugin.yml
   src/main/resources/config.yml
   .github/workflows/build.yml
   ```
5. Commit the upload (the green **Commit changes** button).
6. Click the **Actions** tab at the top of the repo. You should see a
   workflow run start automatically within a few seconds (it's named
   "Build YTBlocks plugin"). Click into it and wait for the green checkmark
   (usually under a minute).
7. Once it's green, scroll down to the **Artifacts** section of that run and
   click **YTBlocks-jar** to download it. Unzip it — inside is
   `YTBlocks.jar`. That's your installable plugin file.

If the build fails (red X instead of green check), click into the failed
step to see the error and send me a screenshot — the most likely cause is
that the exact PaperMC API version for Minecraft 26.2 needs a small version
number adjustment in `pom.xml` (see the comment above the `paper-api`
dependency for where to check available versions).

## Installing on your Aternos server

1. In Aternos, go to **Files**, open the `plugins` folder.
2. Upload `YTBlocks.jar` there.
3. Restart the server once so the plugin generates its config files.
4. Stop the server again, go back into `plugins/YTBlocks/config.yml`, and
   fill in:
   - `youtube.api-key` — your YouTube Data API v3 key
   - `youtube.video-id` — already set to `zPXlDFxJ1Ek`
   - `youtube.channel-handle` — already set to `@cadenyt3662`
   - `region` — already defaults to a 500x500 area centered at (0,0) in the
     world named "world", covering Y levels -64 to 100. Adjust `center-x`/
     `center-z` if you want it centered somewhere other than spawn, or
     `world` if your world has a different name.
5. Start the server. Check the console log for lines starting with
   `[YTBlocks]` to confirm it's running. You can also run `/ytblocks status`
   in-game (needs OP permission) to check progress at any time.

Remember: your video needs to be set to **Public** or **Unlisted** (not
Private) for the API to be able to read its stats.
