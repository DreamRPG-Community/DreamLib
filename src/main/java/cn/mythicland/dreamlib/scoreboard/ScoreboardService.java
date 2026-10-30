package cn.mythicland.dreamlib.scoreboard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks plugin-owned scoreboard sessions and closes them with the Lib lifecycle.
 */
public final class ScoreboardService implements AutoCloseable {

    private final Set<ScoreboardSession> sessions = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    /**
     * Creates and tracks one sidebar scoreboard session.
     *
     * <p>This method must run on the Bukkit primary thread.</p>
     *
     * @param objectiveName objective name accepted by Bukkit
     * @param title         initial display title
     * @return tracked session
     */
    public ScoreboardSession createSession(String objectiveName, String title) {
        if (closed) throw new IllegalStateException("Scoreboard service is closed");
        ScoreboardSession session = new ScoreboardSession(objectiveName, title);
        sessions.add(session);
        return session;
    }

    /**
     * Closes every tracked session.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        sessions.forEach(ScoreboardSession::close);
        sessions.clear();
    }
}
