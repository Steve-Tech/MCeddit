package me.steve8playz.mceddit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.unbescape.html.HtmlEscape;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Objects;

public class MCeddit extends JavaPlugin {
    //Final Variables to put in Config file later
    private File playerConfigFile;
    private FileConfiguration playerConfig;

    @Override
    public void onEnable() {
        getLogger().info("MCeddit " + this.getDescription().getVersion() + " has been Enabled");
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadPlayerConfig();
        getCommand("Reddit").setExecutor(new Commands(this));
        getCommand("RedditPost").setExecutor(new Commands(this));
        getCommand("LinkReddit").setExecutor(new Commands(this));
        this.getServer().getPluginManager().registerEvents(new CakeDay(this), this);
    }

    @Override
    public void onDisable() {
        saveConfig();
        savePlayerConfig();
        getLogger().info("MCeddit " + this.getDescription().getVersion() + " has been Disabled");
    }

    public FileConfiguration getPlayerConfig() {
        return playerConfig;
    }

    public void setPlayerConfig(String arg1, Object arg2) {
        playerConfig.set(arg1, arg2);
    }

    private void loadPlayerConfig() {
        playerConfigFile = new File(getDataFolder(), "players.yml");
        if (!playerConfigFile.exists()) {
            playerConfigFile.getParentFile().mkdirs();
            saveResource("players.yml", false);
        }

        playerConfig = new YamlConfiguration();
        try {
            playerConfig.load(playerConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void savePlayerConfig() {
        playerConfigFile = new File(getDataFolder(), "players.yml");
        try {
            playerConfig.save(playerConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String markDown(String input) {
        return input.replaceFirst("(\\*\\*).*?\\*\\*", ChatColor.BOLD.toString())
                .replaceFirst("(__).*?__", ChatColor.BOLD.toString())
                .replaceFirst(ChatColor.BOLD + ".*?(\\*\\*)", ChatColor.RESET.toString())
                .replaceFirst(ChatColor.BOLD + ".*?(__)", ChatColor.RESET.toString())
                .replaceFirst("(\\*).*?\\*", ChatColor.ITALIC.toString())
                .replaceFirst("(_).*?_", ChatColor.ITALIC.toString())
                .replaceFirst(ChatColor.ITALIC + ".*?(\\*)", ChatColor.RESET.toString())
                .replaceFirst(ChatColor.ITALIC + ".*?(_)", ChatColor.RESET.toString())
                .replaceFirst("(~~).*?~~", ChatColor.STRIKETHROUGH.toString())
                .replaceFirst(ChatColor.STRIKETHROUGH + ".*?(~~)", ChatColor.RESET.toString())
                + ChatColor.RESET;
    }

    // TODO: Make all web requests asynchronous
    public StringBuilder getRedditURL(String link) {
        StringBuilder jsonSB = new StringBuilder();
        try {
            URL url = new URL(link);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-Agent", "MCeddit - A Simple Spigot Plugin to link Minecraft with Reddit");
            conn.connect();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                jsonSB.append(inputLine);
            }
            in.close();

            return jsonSB;

        } catch (Exception e) {
            if (e.getMessage() == null) {
                getLogger().severe("An Unknown Error Occurred while getting to Reddit or Reading the Reddit API, it is possibly because of a bad JSON Object.");
                getLogger().warning("If there are any following errors from this plugin that is a result of this.");
            } else if (e.getMessage() == "www.reddit.com") {
                getLogger().severe("An Error Occurred while getting to Reddit, it is very likely that there is no internet.");
                getLogger().warning("If there are any following errors from this plugin that is a result of this.");
            } else {
                getLogger().severe("An Error Occurred while getting to Reddit: " + e.getMessage());
                getLogger().warning("If there are any following errors from this plugin that is a result of this.");
            }
            return null;
        }
    }

    public JsonObject getRedditURLData(String link) {
        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(getRedditURL(link).toString(), JsonObject.class);
        return jsonObject.get("data").getAsJsonObject();
    }

    public JsonArray getRedditURLArr(String link) {
        Gson gson = new Gson();
        return gson.fromJson(getRedditURL(link).toString(), JsonArray.class);
    }

    public String getReddit(String username, String object) {
        return getRedditURLData("https://www.reddit.com/user/" + username + "/about.json").get(object).getAsString();
    }

    public String[][] getSubreddit(String name, String query) {
        JsonObject data;
        if (query != null) {
            data = getRedditURLData("https://www.reddit.com/r/" + name + ".json?limit=" + getConfig().getInt("PostLimit") +
                    "&" + query);
        } else {
            data = getRedditURLData("https://www.reddit.com/r/" + name + ".json?limit=" + getConfig().getInt("PostLimit"));
        }
        JsonArray jsonPosts = data.get("children").getAsJsonArray();
        String[] post;
        String[][] posts = {};
        for (int i = 0; i < getConfig().getInt("PostLimit"); i++) {
            JsonObject jsonPost = jsonPosts.get(i).getAsJsonObject().get("data").getAsJsonObject();
            post = new String[]{jsonPost.get("subreddit").getAsString(), jsonPost.get("title").getAsString(),
                    jsonPost.get("score").getAsString(), jsonPost.get("author").getAsString(),
                    jsonPost.get("num_comments").getAsString(), jsonPost.get("permalink").getAsString()};

            posts = Arrays.copyOf(posts, posts.length + 1);
            posts[posts.length - 1] = post;
        }

        posts = Arrays.copyOf(posts, posts.length + 1);
        posts[posts.length - 1] = new String[]{data.get("after").getAsString()};

        return posts;
    }

    public String[][] getPost(String permalink) {
        JsonArray data = getRedditURLArr("https://www.reddit.com" + permalink + ".json");
        JsonObject postOnly = data.get(0).getAsJsonObject().get("data").getAsJsonObject().get("children").getAsJsonArray().get(0).getAsJsonObject().get("data").getAsJsonObject();
        JsonArray jsonComments = data.get(1).getAsJsonObject().get("data").getAsJsonObject().get("children").getAsJsonArray();

        String postType;
        String[] postData = new String[3];
        postData[0] = postOnly.get("title").getAsString(); // Post Title

        if (postOnly.has("post_hint")) {
            postType = postOnly.get("post_hint").getAsString();
        } else {
            postType = "";
        }

        postData[2] = postType;
        if (postOnly.has("selftext") && !postOnly.get("selftext").getAsString().isEmpty()) {
            postData[1] = postOnly.get("selftext").getAsString();
        } else if (postOnly.has("url") && !postOnly.get("url").getAsString().isEmpty()) {
            postData[1] = postOnly.get("url").getAsString();
        } else {
            postData[1] = "";
        }

        String[] postText = new String[]{postOnly.get("subreddit").getAsString(), postOnly.get("title").getAsString(),
                postOnly.get("score").getAsString(), postOnly.get("author").getAsString(),
                postOnly.get("num_comments").getAsString(), postOnly.get("permalink").getAsString()};
        String[] postComments = new String[jsonComments.size() + 1];
        for (int i = 0; i < jsonComments.size(); i++){
            try {
                JsonObject jsonComment = jsonComments.get(i).getAsJsonObject().get("data").getAsJsonObject();
                postComments[i] = jsonComment.get("author").getAsString() + ",\t" +
                        HtmlEscape.unescapeHtml(jsonComment.get("body").getAsString().replace('\u00a0',' ').replace('\n', ' ')) + ",\t" + // Replace &nbsp; with a space
                        jsonComment.get("score").getAsString() + ",\t";
            } catch (NullPointerException ignored) {
                postComments[i] = "";
            }
        }

        return new String[][]{postData, postText, postComments};
    }
}