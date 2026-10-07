package dev.cactusguard.storage;

import dev.cactusguard.model.PunishType;
import dev.cactusguard.model.Punishment;
import dev.cactusguard.model.Report;
import dev.cactusguard.model.StaffNote;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite storage for punishments, reports and staff notes.
 * A single connection is shared and guarded by a lock. Callers must invoke these
 * methods off the main server thread (see {@code Async}).
 */
public final class Database implements AutoCloseable {

    private final Connection conn;
    private final ReentrantLock lock = new ReentrantLock();

    public Database(File file) throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create data folder " + parent);
        }
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("""
                CREATE TABLE IF NOT EXISTS punishments (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  target TEXT NOT NULL, target_name TEXT NOT NULL,
                  staff TEXT, staff_name TEXT NOT NULL,
                  type TEXT NOT NULL, reason TEXT NOT NULL,
                  created INTEGER NOT NULL, expires INTEGER NOT NULL,
                  active INTEGER NOT NULL DEFAULT 1, removed_by TEXT)""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_pun_target ON punishments(target)");
            st.execute("""
                CREATE TABLE IF NOT EXISTS reports (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  reporter TEXT NOT NULL, reporter_name TEXT NOT NULL,
                  target TEXT NOT NULL, target_name TEXT NOT NULL,
                  reason TEXT NOT NULL, created INTEGER NOT NULL,
                  status TEXT NOT NULL DEFAULT 'OPEN', handler TEXT, world TEXT)""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS notes (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  target TEXT NOT NULL, target_name TEXT NOT NULL,
                  author TEXT NOT NULL, text TEXT NOT NULL, created INTEGER NOT NULL)""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_notes_target ON notes(target)");
        }
    }

    // ---------------------------------------------------------------- punishments

    public Punishment addPunishment(UUID target, String targetName, UUID staff, String staffName,
                                    PunishType type, String reason, long expires) throws SQLException {
        long now = System.currentTimeMillis();
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO punishments(target,target_name,staff,staff_name,type,reason,created,expires,active)"
                        + " VALUES(?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, target.toString());
            ps.setString(2, targetName);
            if (staff == null) ps.setNull(3, Types.VARCHAR); else ps.setString(3, staff.toString());
            ps.setString(4, staffName);
            ps.setString(5, type.name());
            ps.setString(6, reason);
            ps.setLong(7, now);
            ps.setLong(8, expires);
            // Warnings and kicks are records only; they are never "active" restrictions.
            ps.setInt(9, (type == PunishType.BAN || type == PunishType.MUTE) ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Punishment(rs.getLong(1), target, targetName, staff, staffName, type, reason,
                        now, expires, type == PunishType.BAN || type == PunishType.MUTE, null);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Most recent live punishment of the given type, if any. Expired ones are closed lazily. */
    public Optional<Punishment> activePunishment(UUID target, PunishType type) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM punishments WHERE target=? AND type=? AND active=1 ORDER BY id DESC")) {
            ps.setString(1, target.toString());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Punishment p = readPunishment(rs);
                    if (p.expired()) {
                        deactivate(p.id(), "expired");
                    } else {
                        return Optional.of(p);
                    }
                }
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /** Ends all active punishments of a type for a player. @return how many were ended */
    public int removeActive(UUID target, PunishType type, String by) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE punishments SET active=0, removed_by=? WHERE target=? AND type=? AND active=1")) {
            ps.setString(1, by);
            ps.setString(2, target.toString());
            ps.setString(3, type.name());
            return ps.executeUpdate();
        } finally {
            lock.unlock();
        }
    }

    private void deactivate(long id, String by) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE punishments SET active=0, removed_by=? WHERE id=?")) {
            ps.setString(1, by);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public List<Punishment> history(UUID target, int limit, int offset) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM punishments WHERE target=? ORDER BY id DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, target.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            List<Punishment> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readPunishment(rs));
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    public int countHistory(UUID target) throws SQLException {
        return count("SELECT COUNT(*) FROM punishments WHERE target=?", target.toString());
    }

    public int countWarnings(UUID target) throws SQLException {
        return count("SELECT COUNT(*) FROM punishments WHERE target=? AND type='WARN'", target.toString());
    }

    private Punishment readPunishment(ResultSet rs) throws SQLException {
        String staff = rs.getString("staff");
        return new Punishment(rs.getLong("id"), UUID.fromString(rs.getString("target")),
                rs.getString("target_name"), staff == null ? null : UUID.fromString(staff),
                rs.getString("staff_name"), PunishType.valueOf(rs.getString("type")),
                rs.getString("reason"), rs.getLong("created"), rs.getLong("expires"),
                rs.getInt("active") == 1, rs.getString("removed_by"));
    }

    // ---------------------------------------------------------------- reports

    public Report addReport(UUID reporter, String reporterName, UUID target, String targetName,
                            String reason, String world) throws SQLException {
        long now = System.currentTimeMillis();
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO reports(reporter,reporter_name,target,target_name,reason,created,status,world)"
                        + " VALUES(?,?,?,?,?,?,'OPEN',?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reporter.toString());
            ps.setString(2, reporterName);
            ps.setString(3, target.toString());
            ps.setString(4, targetName);
            ps.setString(5, reason);
            ps.setLong(6, now);
            ps.setString(7, world);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Report(rs.getLong(1), reporter, reporterName, target, targetName, reason, now,
                        Report.Status.OPEN, null, world);
            }
        } finally {
            lock.unlock();
        }
    }

    public Optional<Report> getReport(long id) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM reports WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readReport(rs)) : Optional.empty();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Open and claimed reports, oldest first. */
    public List<Report> pendingReports(int limit) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM reports WHERE status IN ('OPEN','CLAIMED') ORDER BY id ASC LIMIT ?")) {
            ps.setInt(1, limit);
            List<Report> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readReport(rs));
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    public boolean setReportStatus(long id, Report.Status status, String handler) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE reports SET status=?, handler=? WHERE id=?")) {
            ps.setString(1, status.name());
            ps.setString(2, handler);
            ps.setLong(3, id);
            return ps.executeUpdate() > 0;
        } finally {
            lock.unlock();
        }
    }

    /** Count of pending reports filed by this player (for the active-reports cap). */
    public int pendingReportsBy(UUID reporter) throws SQLException {
        return count("SELECT COUNT(*) FROM reports WHERE reporter=? AND status IN ('OPEN','CLAIMED')",
                reporter.toString());
    }

    public int countPendingReports() throws SQLException {
        return count("SELECT COUNT(*) FROM reports WHERE status IN ('OPEN','CLAIMED')", null);
    }

    private Report readReport(ResultSet rs) throws SQLException {
        return new Report(rs.getLong("id"), UUID.fromString(rs.getString("reporter")),
                rs.getString("reporter_name"), UUID.fromString(rs.getString("target")),
                rs.getString("target_name"), rs.getString("reason"), rs.getLong("created"),
                Report.Status.valueOf(rs.getString("status")), rs.getString("handler"),
                rs.getString("world"));
    }

    // ---------------------------------------------------------------- notes

    public StaffNote addNote(UUID target, String targetName, String author, String text) throws SQLException {
        long now = System.currentTimeMillis();
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO notes(target,target_name,author,text,created) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, target.toString());
            ps.setString(2, targetName);
            ps.setString(3, author);
            ps.setString(4, text);
            ps.setLong(5, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new StaffNote(rs.getLong(1), target, targetName, author, text, now);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<StaffNote> notes(UUID target) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM notes WHERE target=? ORDER BY id ASC")) {
            ps.setString(1, target.toString());
            List<StaffNote> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StaffNote(rs.getLong("id"), UUID.fromString(rs.getString("target")),
                            rs.getString("target_name"), rs.getString("author"), rs.getString("text"),
                            rs.getLong("created")));
                }
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    public boolean removeNote(long id) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM notes WHERE id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } finally {
            lock.unlock();
        }
    }

    public int countNotes(UUID target) throws SQLException {
        return count("SELECT COUNT(*) FROM notes WHERE target=?", target.toString());
    }

    // ---------------------------------------------------------------- stats

    public int totalPunishments() throws SQLException { return count("SELECT COUNT(*) FROM punishments", null); }

    public int totalReports() throws SQLException { return count("SELECT COUNT(*) FROM reports", null); }

    public int totalNotes() throws SQLException { return count("SELECT COUNT(*) FROM notes", null); }

    private int count(String sql, String arg) throws SQLException {
        lock.lock();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (arg != null) ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            conn.close();
        } catch (SQLException ignored) {
            // shutting down
        } finally {
            lock.unlock();
        }
    }
}
