package com.caden.ytblocks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YTBlocksPlugin extends JavaPlugin {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private File dataFile;
    private YamlConfiguration data;

    private String apiKey;
    private String videoId;
    private String channelHandle;
    private int perView, perLike, perSub;
    private int pollIntervalSeconds;
    private int maxScanPerCheck;

    private String worldName;
    private int centerX, centerZ, size;
    private int x1, x2, z1, z2;
    private int yMin, yMax;
    private Set<Material> ignoreBlocks = EnumSet.noneOf(Material.class);

    private volatile String channelId;
    private volatile long lastViews = -1, lastLikes = -1, lastSubs = -1;
    private volatile long scanCursor = 0;
    private volatile long pendingCredit = 0;
    private volatile long blocksRemoved = 0;

    private long width, depth, height, totalCells;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        loadData();
        applyWorldBorder();

        getLogger().info("YTBlocks enabled. Play area is " + size + "x" + size
                + " (" + totalCells + " scannable positions). " + blocksRemoved + " blocks removed so far.");

        Bukkit.getScheduler().runTaskTimerAsynchronously(
                this, this::pollAndApply, 100L, pollIntervalSeconds * 20L);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadSettings() {
        FileConfiguration cfg = getConfig();
        apiKey = cfg.getString("youtube.api-key", "");
        videoId = cfg.getString("youtube.video-id", "");
        channelHandle = cfg.getString("youtube.channel-handle", "");

        perView = cfg.getInt("points.per-view", 1);
        perLike = cfg.getInt("points.per-like", 10);
        perSub = cfg.getInt("points.per-sub", 50);

        pollIntervalSeconds = Math.max(15, cfg.getInt("poll-interval-seconds", 60));
        maxScanPerCheck = Math.max(1000, cfg.getInt("max-scan-per-check", 20000));

        worldName = cfg.getString("region.world", "world");
        centerX = cfg.getInt("region.center-x", 0);
        centerZ = cfg.getInt("region.center-z", 0);
        size = Math.max(1, cfg.getInt("region.size", 500));
        yMin = cfg.getInt("region.y-min", -64);
        yMax = cfg.getInt("region.y-max", 100);
        if (yMax < yMin) {
            int tmp = yMax;
            yMax = yMin;
            yMin = tmp;
        }

        int half = size / 2;
        x1 = centerX - half;
        x2 = x1 + size - 1;
        z1 = centerZ - half;
        z2 = z1 + size - 1;

        ignoreBlocks = EnumSet.noneOf(Material.class);
        List<String> ignoreNames = cfg.getStringList("region.ignore-blocks");
        for (String name : ignoreNames) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                ignoreBlocks.add(m);
            }
        }

        width = size;
        depth = size;
        height = (long) (yMax - yMin) + 1;
        totalCells = width * depth * height;
    }

    private void applyWorldBorder() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("YTBlocks: world '" + worldName + "' not found - skipping world border setup.");
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX + 0.5, centerZ + 0.5);
        border.setSize(size);
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not create data.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        lastViews = data.getLong("last-views", -1);
        lastLikes = data.getLong("last-likes", -1);
        lastSubs = data.getLong("last-subs", -1);
        scanCursor = data.getLong("scan-cursor", 0);
        pendingCredit = data.getLong("pending-credit", 0);
        blocksRemoved = data.getLong("blocks-removed", 0);
        channelId = data.getString("resolved-channel-id", null);
    }

    private synchronized void saveData() {
        data.set("last-views", lastViews);
        data.set("last-likes", lastLikes);
        data.set("last-subs", lastSubs);
        data.set("scan-cursor", scanCursor);
        data.set("pending-credit", pendingCredit);
        data.set("blocks-removed", blocksRemoved);
        if (channelId != null) {
            data.set("resolved-channel-id", channelId);
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not save data.yml", e);
        }
    }

    private void pollAndApply() {
        try {
            if (apiKey == null || apiKey.isBlank() || apiKey.equals("PUT_YOUR_API_KEY_HERE")) {
                getLogger().warning("YTBlocks: no API key set in config.yml yet - skipping check.");
                return;
            }

            if (channelId == null) {
                channelId = resolveChannelId(channelHandle);
                if (channelId == null) {
                    getLogger().warning("YTBlocks: could not resolve channel handle '"
                            + channelHandle + "' yet - will retry next check.");
                }
            }

            long[] videoStats = fetchVideoStats(videoId);
            long views = videoStats[0];
            long likes = videoStats[1];
            long subs = (channelId != null) ? fetchSubscriberCount(channelId) : Math.max(lastSubs, 0);

            if (lastViews < 0) {
                lastViews = views;
                lastLikes = likes;
                lastSubs = subs;
                saveData();
                getLogger().info("YTBlocks: baseline set (views=" + views
                        + ", likes=" + likes + ", subs=" + subs + "). Future growth will remove blocks.");
                return;
            }

            long deltaViews = Math.max(0, views - lastViews);
            long deltaLikes = Math.max(0, likes - lastLikes);
            long deltaSubs = Math.max(0, subs - lastSubs);

            lastViews = views;
            lastLikes = likes;
            lastSubs = subs;

            long newCredit = deltaViews * perView + deltaLikes * perLike + deltaSubs * perSub;
            pendingCredit += newCredit;

            if (pendingCredit <= 0) {
                saveData();
                return;
            }

            if (scanCursor >= totalCells) {
                saveData();
                getLogger().info("YTBlocks: play area fully scanned/cleared - nothing left to carve.");
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> applyCarve(deltaViews, deltaLikes, deltaSubs));

        } catch (Exception e) {
            getLogger().log(Level.WARNING, "YTBlocks: poll failed", e);
        }
    }

    private void applyCarve(long dViews, long dLikes, long dSubs) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("YTBlocks: world '" + worldName + "' not found - cannot remove blocks.");
            return;
        }

        long removedThisPass = 0;
        long scannedThisPass = 0;

        while (pendingCredit > 0 && scanCursor < totalCells && scannedThisPass < maxScanPerCheck) {
            long perColumn = height;
            long columnIndex = scanCursor / perColumn;
            long withinColumn = scanCursor % perColumn;

            long localX = columnIndex / depth;
            long localZ = columnIndex % depth;

            int bx = x1 + (int) localX;
            int bz = z1 + (int) localZ;
            int by = yMax - (int) withinColumn;

            Block block = world.getBlockAt(bx, by, bz);
            Material type = block.getType();
            if (type != Material.AIR && !ignoreBlocks.contains(type)) {
                block.setType(Material.AIR, false);
                removedThisPass++;
                pendingCredit--;
            }

            scanCursor++;
            scannedThisPass++;
        }

        blocksRemoved += removedThisPass;
        saveData();

        getLogger().info("YTBlocks: removed " + removedThisPass + " blocks this check (views +" + dViews
                + ", likes +" + dLikes + ", subs +" + dSubs + "). Total removed: " + blocksRemoved
                + ". Scan progress: " + scanCursor + "/" + totalCells
                + (pendingCredit > 0 ? (". " + pendingCredit + " blocks still owed, will continue next check.") : "."));
    }

    private long[] fetchVideoStats(String videoId) throws IOException, InterruptedException {
        String url = "https://www.googleapis.com/youtube/v3/videos?part=statistics&id="
                + videoId + "&key=" + apiKey;
        String body = get(url);
        long views = extractLong(body, "\"viewCount\":\\s*\"(\\d+)\"");
        long likes = extractLong(body, "\"likeCount\":\\s*\"(\\d+)\"");
        return new long[]{views, likes};
    }

    private long fetchSubscriberCount(String channelId) throws IOException, InterruptedException {
        String url = "https://www.googleapis.com/youtube/v3/channels?part=statistics&id="
                + channelId + "&key=" + apiKey;
        String body = get(url);
        return extractLong(body, "\"subscriberCount\":\\s*\"(\\d+)\"");
    }

    private String resolveChannelId(String handle) throws IOException, InterruptedException {
        if (handle == null || handle.isBlank()) {
            return null;
        }
        String cleanHandle = handle.startsWith("@") ? handle.substring(1) : handle;
        String url = "https://www.googleapis.com/youtube/v3/channels?part=id&forHandle="
                + cleanHandle + "&key=" + apiKey;
        String body = get(url);
        Matcher m = Pattern.compile("\"id\":\\s*\"(UC[\\w-]+)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("YouTube API returned HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    private long extractLong(String body, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(body);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSettings();
            applyWorldBorder();
            sender.sendMessage("Config reloaded.");
            return true;
        }

        sender.sendMessage("Blocks removed: " + blocksRemoved + ". Scan progress: "
                + scanCursor + "/" + totalCells + ". Blocks owed: " + pendingCredit);
        sender.sendMessage("Last known - views: " + lastViews
                + ", likes: " + lastLikes + ", subs: " + lastSubs);
        return true;
    }
}
