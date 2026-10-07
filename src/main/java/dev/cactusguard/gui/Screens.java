package dev.cactusguard.gui;

import dev.cactusguard.Services;
import dev.cactusguard.model.PunishType;
import dev.cactusguard.model.Punishment;
import dev.cactusguard.model.Report;
import dev.cactusguard.model.StaffNote;
import dev.cactusguard.util.Durations;
import dev.cactusguard.util.Font;
import dev.cactusguard.util.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Every CactusGuard screen, in the order a staff member meets them:
 * Hub -> Players -> PlayerMenu -> (DurationMenu ->) ReasonMenu, plus Reports, History and Notes.
 * Screens only gather choices; all real work happens in {@link Services}.
 */
public final class Screens {

    /** A resolved player: uuid + display name. */
    public record Who(UUID id, String name) {}

    private static final String[] DURATIONS = {"15m", "1h", "6h", "1d", "7d", "30d"};

    private final Services svc;
    private final ChatPrompt prompt;

    public Screens(Services svc, ChatPrompt prompt) {
        this.svc = svc;
        this.prompt = prompt;
    }

    // ------------------------------------------------------------ entry points

    public void hub(Player p) { new Hub(p).open(); }

    public void players(Player p, int page) { new PlayersMenu(p, page).open(); }

    public void player(Player p, Who who) { new PlayerMenu(p, who).open(); }

    public void reports(Player p) {
        svc.async(() -> svc.db().pendingReports(45), list -> new ReportsMenu(p, list).open(),
                err -> p.sendMessage(Component.text(err)));
    }

    public void history(Player p, Who who) {
        svc.async(() -> svc.db().history(who.id(), 45, 0), list -> new HistoryMenu(p, who, list).open(),
                err -> p.sendMessage(Component.text(err)));
    }

    public void notes(Player p, Who who) {
        svc.async(() -> svc.db().notes(who.id()), list -> new NotesMenu(p, who, list).open(),
                err -> p.sendMessage(Component.text(err)));
    }

    // ------------------------------------------------------------ shared bits

    private String t(String key, String... args) { return svc.msg().text(key, args); }

    private Component heading(String key, String... args) {
        return Component.text(t(key, args), Theme.GREEN);
    }

    private static ItemStack head(OfflinePlayer op, Component name, Component... lore) {
        ItemStack it = Menu.icon(Material.PLAYER_HEAD, name, lore);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(op);
        it.setItemMeta(m);
        return it;
    }

    private static String ago(long ms) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(ms));
    }

    /** Menu with the shared back/close buttons. */
    private abstract class Base extends Menu {
        Base(Player v) { super(v); }

        void back(int slot, Runnable to) {
            set(slot, icon(Material.ARROW, green(Font.small(t("gui.back")))), e -> to.run());
        }

        void close(int slot) {
            set(slot, icon(Material.BARRIER, Theme.item(Font.small(t("gui.close")), Theme.RED, true)),
                    e -> viewer.closeInventory());
        }
    }

    // ------------------------------------------------------------ hub

    private final class Hub extends Base {
        Hub(Player v) { super(v); }

        @Override protected Component title() { return heading("gui.hub-title"); }

        @Override protected int rows() { return 3; }

        @Override protected void draw() {
            boolean reports = viewer.hasPermission("cactusguard.reports.manage");
            set(11, icon(Material.PLAYER_HEAD, green(Font.small("players")),
                    gray("Warn, mute, kick, ban"), gray("and look up history")),
                    e -> players(viewer, 0));
            if (reports) {
                set(13, icon(Material.WRITABLE_BOOK, green(Font.small("reports")),
                        gray("Review player reports")), e -> reports(viewer));
            }
            set(15, icon(Material.CACTUS, green(Font.small("about")),
                    gray("Punishments, reports,"), gray("staff notes and Discord logs")));
            footer();
            close(22);
        }
    }

    // ------------------------------------------------------------ players list

    private final class PlayersMenu extends Base {
        private static final int PER_PAGE = 36;
        private final int page;

        PlayersMenu(Player v, int page) {
            super(v);
            this.page = page;
        }

        @Override protected Component title() { return heading("gui.players-title"); }

        @Override protected int rows() { return 5; }

        @Override protected void draw() {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            online.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            int from = page * PER_PAGE;
            int slot = 0;
            for (int i = from; i < Math.min(online.size(), from + PER_PAGE); i++) {
                Player target = online.get(i);
                set(slot++, head(target, green(target.getName()), lime(t("gui.click-open"))),
                        e -> player(viewer, new Who(target.getUniqueId(), target.getName())));
            }
            if (page > 0) {
                set(36, icon(Material.ARROW, green(Font.small(t("gui.prev")))), e -> players(viewer, page - 1));
            }
            if (from + PER_PAGE < online.size()) {
                set(44, icon(Material.ARROW, green(Font.small(t("gui.next")))), e -> players(viewer, page + 1));
            }
            footer();
            back(40, () -> hub(viewer));
        }
    }

    // ------------------------------------------------------------ one player

    private final class PlayerMenu extends Base {
        private final Who who;

        PlayerMenu(Player v, Who who) {
            super(v);
            this.who = who;
        }

        @Override protected Component title() { return heading("gui.player-title", "name", who.name()); }

        @Override protected int rows() { return 4; }

        @Override protected void draw() {
            OfflinePlayer op = Bukkit.getOfflinePlayer(who.id());
            set(4, head(op, green(who.name())));

            if (viewer.hasPermission("cactusguard.punish")) {
                set(10, icon(Material.PAPER, Theme.item(Font.small("warn"), Theme.LIME, true)),
                        e -> new ReasonMenu(viewer, who, PunishType.WARN, 0).open());
                set(11, icon(Material.NAME_TAG, Theme.item(Font.small("mute"), Theme.LIME, true)),
                        e -> new DurationMenu(viewer, who, PunishType.MUTE).open());
                set(12, icon(Material.LEATHER_BOOTS, Theme.item(Font.small("kick"), Theme.LIME, true)),
                        e -> new ReasonMenu(viewer, who, PunishType.KICK, 0).open());
                set(13, icon(Material.IRON_BARS, Theme.item(Font.small("ban"), Theme.RED, true)),
                        e -> new DurationMenu(viewer, who, PunishType.BAN).open());
            }
            if (viewer.hasPermission("cactusguard.unpunish")) {
                set(14, icon(Material.EMERALD, green(Font.small("unmute"))), e -> pardon(PunishType.MUTE));
                set(15, icon(Material.LIME_DYE, green(Font.small("unban"))), e -> pardon(PunishType.BAN));
            }
            if (viewer.hasPermission("cactusguard.history")) {
                set(19, icon(Material.BOOK, green(Font.small("history"))), e -> history(viewer, who));
            }
            if (viewer.hasPermission("cactusguard.notes")) {
                set(20, icon(Material.WRITABLE_BOOK, green(Font.small("notes"))), e -> notes(viewer, who));
            }
            footer();
            back(31, () -> players(viewer, 0));
        }

        private void pardon(PunishType type) {
            svc.pardon(viewer.getName(), who.id(), who.name(), type, removed -> {
                viewer.closeInventory();
                if (removed == 0) {
                    viewer.sendMessage(svc.msg().msg("punish.nothing-active", "name", who.name(),
                            "what", type.label().toLowerCase(Locale.ROOT)));
                } else {
                    viewer.sendMessage(svc.msg().msg("punish.pardon-done", "name", who.name(),
                            "state", type == PunishType.BAN ? "banned" : "muted"));
                }
            }, err -> viewer.sendMessage(Component.text(err)));
        }
    }

    // ------------------------------------------------------------ duration picker

    private final class DurationMenu extends Base {
        private final Who who;
        private final PunishType type;

        DurationMenu(Player v, Who who, PunishType type) {
            super(v);
            this.who = who;
            this.type = type;
        }

        @Override protected Component title() { return heading("gui.duration-title"); }

        @Override protected int rows() { return 3; }

        @Override protected void draw() {
            int slot = 10;
            for (String d : DURATIONS) {
                long ms = Durations.parse(d);
                set(slot++, icon(Material.CLOCK, green(Font.small(d))),
                        e -> new ReasonMenu(viewer, who, type, ms).open());
            }
            set(16, icon(Material.NETHER_STAR, Theme.item(Font.small("permanent"), Theme.RED, true)),
                    e -> new ReasonMenu(viewer, who, type, 0).open());
            footer();
            back(22, () -> player(viewer, who));
        }
    }

    // ------------------------------------------------------------ reason picker

    private final class ReasonMenu extends Base {
        private final Who who;
        private final PunishType type;
        private final long duration;

        ReasonMenu(Player v, Who who, PunishType type, long duration) {
            super(v);
            this.who = who;
            this.type = type;
            this.duration = duration;
        }

        @Override protected Component title() { return heading("gui.reason-title"); }

        @Override protected int rows() { return 4; }

        @Override protected void draw() {
            List<String> reasons = svc.config().getStringList("punishments.reason-presets");
            if (reasons.isEmpty()) reasons = List.of(svc.defaultReason());
            int slot = 0;
            for (String r : reasons) {
                if (slot >= 18) break;
                set(slot++, icon(Material.LIME_CONCRETE, green(r)), e -> apply(r));
            }
            set(26, icon(Material.OAK_SIGN, Theme.item(Font.small("custom"), Theme.LIME, true),
                    gray("Type your own reason")), e -> prompt.ask(viewer, "gui.prompt-reason", this::apply));
            footer();
            back(31, () -> player(viewer, who));
        }

        private void apply(String reason) {
            viewer.closeInventory();
            UUID staff = viewer.getUniqueId();
            svc.punish(viewer.getName(), staff, who.id(), who.name(), type, reason, duration,
                    p -> viewer.sendMessage(svc.msg().msg("punish.done", "id", String.valueOf(p.id()),
                            "name", who.name())),
                    err -> viewer.sendMessage(Component.text(err)));
        }
    }

    // ------------------------------------------------------------ reports

    private final class ReportsMenu extends Base {
        private final List<Report> list;

        ReportsMenu(Player v, List<Report> list) {
            super(v);
            this.list = list;
        }

        @Override protected Component title() { return heading("gui.reports-title"); }

        @Override protected int rows() { return 6; }

        @Override protected void draw() {
            int slot = 0;
            for (Report r : list) {
                if (slot >= 45) break;
                set(slot++, head(Bukkit.getOfflinePlayer(r.target()),
                        green("#" + r.id() + " " + r.targetName()),
                        gray(r.reason()),
                        gray(r.reporterName() + " \u00b7 " + ago(r.created())),
                        lime(r.status().name().toLowerCase(Locale.ROOT)
                                + (r.handler() == null ? "" : " (" + r.handler() + ")")),
                        Component.empty(),
                        Theme.item(t("gui.click-claim"), Theme.GREEN),
                        Theme.item(t("gui.click-resolve"), Theme.GREEN),
                        Theme.item(t("gui.click-dismiss"), Theme.RED)),
                        e -> act(e, r.id()));
            }
            footer();
            set(49, icon(list.isEmpty() ? Material.CACTUS : Material.WRITABLE_BOOK,
                    green(list.isEmpty() ? t("gui.all-clear") : t("gui.pending", "count", String.valueOf(list.size())))));
            back(53, () -> hub(viewer));
        }

        private void act(InventoryClickEvent e, long id) {
            Report.Status s = e.isRightClick() ? Report.Status.DISMISSED
                    : e.isShiftClick() ? Report.Status.RESOLVED : Report.Status.CLAIMED;
            svc.updateReport(viewer.getName(), id, s, r -> {
                if (r == null) viewer.sendMessage(svc.msg().msg("report.not-found", "id", String.valueOf(id)));
                reports(viewer); // refresh in place
            }, err -> viewer.sendMessage(Component.text(err)));
        }
    }

    // ------------------------------------------------------------ history

    private final class HistoryMenu extends Base {
        private final Who who;
        private final List<Punishment> list;

        HistoryMenu(Player v, Who who, List<Punishment> list) {
            super(v);
            this.who = who;
            this.list = list;
        }

        @Override protected Component title() { return heading("gui.history-title"); }

        @Override protected int rows() { return 6; }

        @Override protected void draw() {
            int slot = 0;
            for (Punishment p : list) {
                if (slot >= 45) break;
                boolean live = p.isLive();
                Material m = switch (p.type()) {
                    case BAN -> Material.IRON_BARS;
                    case MUTE -> Material.NAME_TAG;
                    case WARN -> Material.PAPER;
                    case KICK -> Material.LEATHER_BOOTS;
                };
                String dur = p.permanent() || p.type() == PunishType.WARN || p.type() == PunishType.KICK ? ""
                        : " (" + Durations.format(p.expires() - p.created()) + ")";
                set(slot++, icon(m, Theme.item("#" + p.id() + " " + p.type().label() + dur,
                                live ? Theme.GREEN : Theme.GRAY, live),
                        gray(p.reason()),
                        gray(p.staffName() + " \u00b7 " + ago(p.created())),
                        live ? lime("active") : Theme.item("ended", Theme.DARK)));
            }
            footer();
            back(49, () -> player(viewer, who));
        }
    }

    // ------------------------------------------------------------ notes

    private final class NotesMenu extends Base {
        private final Who who;
        private final List<StaffNote> list;

        NotesMenu(Player v, Who who, List<StaffNote> list) {
            super(v);
            this.who = who;
            this.list = list;
        }

        @Override protected Component title() { return heading("gui.notes-title"); }

        @Override protected int rows() { return 6; }

        @Override protected void draw() {
            int slot = 0;
            for (StaffNote n : list) {
                if (slot >= 45) break;
                set(slot++, icon(Material.PAPER, green("#" + n.id() + " " + n.author()),
                        gray(n.text()), lime(ago(n.created())), Component.empty(),
                        Theme.item("Shift-click: delete", Theme.RED)),
                        e -> {
                            if (!e.isShiftClick()) return;
                            svc.async(() -> svc.db().removeNote(n.id()), ok -> notes(viewer, who),
                                    err -> viewer.sendMessage(Component.text(err)));
                        });
            }
            set(53, icon(Material.OAK_SIGN, Theme.item(Font.small("add note"), Theme.LIME, true)),
                    e -> prompt.ask(viewer, "gui.prompt-note", text ->
                            svc.async(() -> svc.db().addNote(who.id(), who.name(), viewer.getName(), text), n -> {
                                svc.noteAdded(n);
                                viewer.sendMessage(svc.msg().msg("notes.saved", "id", String.valueOf(n.id()),
                                        "name", who.name()));
                                notes(viewer, who);
                            }, err -> viewer.sendMessage(Component.text(err)))));
            footer();
            back(49, () -> player(viewer, who));
        }
    }
}
