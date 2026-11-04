package pl.chat2discord.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Chat2DiscordPlugin extends JavaPlugin implements Listener {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static class Settings {
        String webhookUrl = "";
        List<String> commands = new ArrayList<>();
        String chatMode = "off";
        List<String> triggers = new ArrayList<>();
        String match = "starts_with";
        boolean stripTrigger = true;
        String format = "**{player}** wysłał: {message}";
        boolean notifyPlayer = true;
        String notifyText = "&a✔ Wysłano na Discord";
        boolean adminView = true;
        String adminFormat = "&8[&bC2D&8] &f{player}&7: &f{message}";
        boolean logToConsole = true;
    }

    private volatile Settings s = new Settings();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void load() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        Settings n = new Settings();

        n.webhookUrl = cfg.getString("webhook_url", "").trim();
        for (String c : cfg.getStringList("commands")) {
            String name = c.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) name = name.substring(1);
            if (name.matches("[a-z0-9_\\-]+")) {
                n.commands.add(name);
                if (Bukkit.getCommandMap().getCommand(name) != null) {
                    getLogger().warning("Komenda /" + name + " juz istnieje na serwerze - zostanie nadpisana przez Chat2Discord.");
                }
            } else {
                getLogger().warning("Pominieto niepoprawna nazwe komendy: " + c);
            }
        }
        n.chatMode = cfg.getString("chat.mode", "off").trim().toLowerCase(Locale.ROOT);
        for (String t : cfg.getStringList("chat.triggers")) {
            if (!t.isBlank()) n.triggers.add(t.trim());
        }
        n.match = cfg.getString("chat.match", "starts_with").trim().toLowerCase(Locale.ROOT);
        n.stripTrigger = cfg.getBoolean("chat.strip_trigger", true);
        n.format = cfg.getString("format", n.format);
        n.notifyPlayer = cfg.getBoolean("notify_player", true);
        n.notifyText = cfg.getString("notify_text", n.notifyText);
        n.adminView = cfg.getBoolean("admin_view.enabled", true);
        n.adminFormat = cfg.getString("admin_view.format", n.adminFormat);
        n.logToConsole = cfg.getBoolean("admin_view.log_to_console", true);

        s = n;
        if (n.webhookUrl.isEmpty()) getLogger().warning("Brak webhook_url - wiadomosci beda widoczne tylko na serwerze.");
        getLogger().info("Komendy: " + n.commands + ", czat: " + n.chatMode);
    }

    // ---------- komendy wpisywane przez graczy (/dc cokolwiek) ----------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Settings c = s;
        String raw = e.getMessage();
        if (!raw.startsWith("/")) return;

        String body = raw.substring(1).trim();
        int sp = body.indexOf(' ');
        String label = (sp < 0 ? body : body.substring(0, sp)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!c.commands.contains(label)) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("chat2discord.use")) {
            p.sendMessage(Component.text("Nie masz uprawnień do tej komendy.", NamedTextColor.RED));
            return;
        }
        String msg = sp < 0 ? "" : body.substring(sp + 1).trim();
        if (msg.isEmpty()) {
            p.sendMessage(Component.text("Użycie: /" + label + " <wiadomość>", NamedTextColor.RED));
            return;
        }
        dispatch(c, p, msg);
    }

    // ---------- zwykly czat (opcjonalnie) ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Settings c = s;
        if (c.chatMode.equals("off")) return;

        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (c.chatMode.equals("filter")) {
            text = applyFilter(c, text);
            if (text == null) return;
        }
        if (text.isBlank()) return;
        dispatch(c, e.getPlayer(), text);
    }

    private void dispatch(Settings c, Player p, String msg) {
        String nick = p.getName();

        if (!c.webhookUrl.isBlank()) {
            String out = c.format
                    .replace("{player}", escapeMd(nick))
                    .replace("{message}", msg);
            send(c.webhookUrl, out);
        }

        if (c.logToConsole) getLogger().info("[C2D] " + nick + ": " + msg);

        Bukkit.getScheduler().runTask(this, () -> {
            if (c.adminView) {
                Component line = LegacyComponentSerializer.legacyAmpersand().deserialize(c.adminFormat)
                        .replaceText(b -> b.matchLiteral("{player}").replacement(Component.text(nick)))
                        .replaceText(b -> b.matchLiteral("{message}").replacement(Component.text(msg)));
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("chat2discord.see")) online.sendMessage(line);
                }
            }
            if (c.notifyPlayer && p.isOnline()) {
                p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(c.notifyText));
            }
        });
    }

    private static String applyFilter(Settings c, String text) {
        String lowerFull = text.toLowerCase(Locale.ROOT);
        String lower = lowerFull.trim();
        for (String trigger : c.triggers) {
            String t = trigger.toLowerCase(Locale.ROOT);
            boolean ok = switch (c.match) {
                case "contains" -> lower.contains(t);
                case "equals" -> lower.equals(t);
                default -> lower.startsWith(t);
            };
            if (!ok) continue;
            if (!c.stripTrigger) return text;
            int idx = lowerFull.indexOf(t);
            if (idx < 0) return text;
            return (text.substring(0, idx) + text.substring(idx + t.length())).trim();
        }
        return null;
    }

    // ---------- /chat2discord reload ----------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            load();
            sender.sendMessage(Component.text("Chat2Discord: wczytano konfigurację ponownie.", NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Użycie: /" + label + " reload", NamedTextColor.RED));
        return true;
    }

    private void send(String url, String content) {
        try {
            String json = "{\"content\":\"" + jsonEscape(content) + "\",\"allowed_mentions\":{\"parse\":[]}}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally(ex -> {
                getLogger().warning("Nie udalo sie wyslac na Discorda: " + ex.getMessage());
                return null;
            });
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Niepoprawny webhook_url w config.yml");
        }
    }

    private static String escapeMd(String s) {
        return s.replace("_", "\\_").replace("*", "\\*");
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
