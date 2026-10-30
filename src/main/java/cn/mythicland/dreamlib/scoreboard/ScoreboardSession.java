package cn.mythicland.dreamlib.scoreboard;

import cn.mythicland.dreamlib.text.LegacyText;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * Owns one Bukkit sidebar scoreboard, its rendered lines, viewers, and teams.
 * All methods must be called on the Bukkit primary thread.
 */
public final class ScoreboardSession implements AutoCloseable {

    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<String> renderedEntries = new ArrayList<>();
    private final Map<String, Team> teams = new HashMap<>();
    private final Set<Player> viewers = new HashSet<>();
    private final Map<String, Integer> belowNameScores = new HashMap<>();
    private String title;
    private Objective belowNameObjective;
    private String belowNameCriteria;
    private boolean closed;

    ScoreboardSession(String objectiveName, String title) {
        Objects.requireNonNull(objectiveName, "objectiveName");
        Objects.requireNonNull(title, "title");
        if (objectiveName.isBlank() || objectiveName.length() > 16) {
            throw new IllegalArgumentException("Scoreboard objectiveName must contain 1 to 16 characters");
        }
        String translatedTitle = LegacyText.colorize(title);
        if (translatedTitle.length() > 32) {
            throw new IllegalArgumentException("Scoreboard title cannot exceed 32 characters");
        }
        if (Bukkit.getScoreboardManager() == null) {
            throw new IllegalStateException("Bukkit scoreboard manager is unavailable");
        }
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective(objectiveName, "dummy");
        this.title = translatedTitle;
        this.objective.setDisplayName(this.title);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    private static String createEntry(String line, int index) {
        String suffix = ChatColor.COLOR_CHAR + Integer.toHexString(index);
        if (line.length() + suffix.length() > 40) {
            throw new IllegalArgumentException("Scoreboard line cannot exceed 38 characters: " + line);
        }
        return line + suffix;
    }

    /**
     * Sets the sidebar title.
     *
     * @param title legacy title text
     */
    public void setTitle(String title) {
        ensureOpen();
        String translatedTitle = LegacyText.colorize(Objects.requireNonNull(title, "title"));
        if (translatedTitle.length() > 32) {
            throw new IllegalArgumentException("Scoreboard title cannot exceed 32 characters");
        }
        if (!this.title.equals(translatedTitle)) {
            objective.setDisplayName(translatedTitle);
            this.title = translatedTitle;
        }
    }

    /**
     * Displays one objective below every player's name on this session's scoreboard.
     *
     * <p>Changing the criteria unregisters the previous below-name objective because Bukkit
     * objectives cannot change criteria after registration. Reusing the same criteria only
     * updates the display metadata and keeps the client-side objective stable.</p>
     *
     * @param criteria    Bukkit scoreboard criteria, for example {@code health}
     * @param displayName translated objective label shown below player names
     */
    public void setBelowName(String criteria, String displayName) {
        ensureOpen();
        String normalizedCriteria = Objects.requireNonNull(criteria, "criteria").trim();
        if (normalizedCriteria.isBlank()) throw new IllegalArgumentException("Below-name criteria cannot be blank");
        String translatedName = LegacyText.colorize(Objects.requireNonNull(displayName, "displayName"));
        if (translatedName.length() > 32) {
            throw new IllegalArgumentException("Below-name display name cannot exceed 32 characters");
        }
        if (belowNameObjective == null || !normalizedCriteria.equals(belowNameCriteria)) {
            unregisterBelowNameObjective();
            belowNameObjective = scoreboard.registerNewObjective("drpg_below", normalizedCriteria);
            belowNameObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
            belowNameCriteria = normalizedCriteria;
        }
        if (!translatedName.equals(belowNameObjective.getDisplayName())) {
            belowNameObjective.setDisplayName(translatedName);
        }
    }

    /**
     * Removes the optional below-name objective from this session.
     */
    public void clearBelowName() {
        ensureOpen();
        unregisterBelowNameObjective();
    }

    /**
     * Synchronizes scores for the current below-name objective.
     *
     * <p>The objective must use the mutable {@code dummy} criterion. Removed entries are reset
     * from this session scoreboard, while unchanged entries are left untouched to avoid client
     * churn when a viewer joins an already populated server.</p>
     *
     * @param scores entry names and their desired integer scores
     */
    public void setBelowNameScores(Map<String, Integer> scores) {
        ensureOpen();
        Objects.requireNonNull(scores, "scores");
        if (belowNameObjective == null) {
            throw new IllegalStateException("Below-name objective has not been configured");
        }
        if (!"dummy".equals(belowNameCriteria)) {
            throw new IllegalStateException("Below-name scores require the dummy criterion");
        }
        Map<String, Integer> desiredScores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "below-name entry");
            Integer score = Objects.requireNonNull(entry.getValue(), "below-name score");
            if (name.isBlank()) throw new IllegalArgumentException("Below-name entry cannot be blank");
            desiredScores.put(name, score);
        }
        for (String entry : new HashSet<>(belowNameScores.keySet())) {
            if (!desiredScores.containsKey(entry)) scoreboard.resetScores(entry);
        }
        for (Map.Entry<String, Integer> entry : desiredScores.entrySet()) {
            if (entry.getValue().equals(belowNameScores.get(entry.getKey()))) continue;
            belowNameObjective.getScore(entry.getKey()).setScore(entry.getValue());
        }
        belowNameScores.clear();
        belowNameScores.putAll(desiredScores);
    }

    /**
     * Synchronizes sidebar lines with the supplied rendered values. The first list item is the top line.
     *
     * @param lines at most fifteen lines
     */
    public void setLines(List<String> lines) {
        ensureOpen();
        Objects.requireNonNull(lines, "lines");
        if (lines.size() > 15) throw new IllegalArgumentException("Scoreboard cannot contain more than 15 lines");

        List<String> nextEntries = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String line = LegacyText.colorize(Objects.requireNonNull(lines.get(index), "line"));
            nextEntries.add(createEntry(line, index));
        }

        for (int index = nextEntries.size(); index < renderedEntries.size(); index++) {
            scoreboard.resetScores(renderedEntries.get(index));
        }
        for (int index = 0; index < Math.min(renderedEntries.size(), nextEntries.size()); index++) {
            String previousEntry = renderedEntries.get(index);
            String nextEntry = nextEntries.get(index);
            if (!previousEntry.equals(nextEntry)) scoreboard.resetScores(previousEntry);
        }
        for (int index = 0; index < nextEntries.size(); index++) {
            String entry = nextEntries.get(index);
            int desiredScore = nextEntries.size() - index;
            Score score = objective.getScore(entry);
            if (!score.isScoreSet() || score.getScore() != desiredScore) score.setScore(desiredScore);
        }
        renderedEntries.clear();
        renderedEntries.addAll(nextEntries);
    }

    /**
     * Shows this session to one player.
     *
     * @param player viewer
     */
    public void show(Player player) {
        ensureOpen();
        Player viewer = Objects.requireNonNull(player, "player");
        if (viewer.getScoreboard() != scoreboard) viewer.setScoreboard(scoreboard);
        viewers.add(viewer);
    }

    /**
     * Restores the server main scoreboard for one viewer when this session is active.
     *
     * @param player viewer
     */
    public void hide(Player player) {
        ensureOpen();
        Objects.requireNonNull(player, "player");
        if (player.getScoreboard() == scoreboard && Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        viewers.remove(player);
    }

    /**
     * Replaces one team and its entries.
     *
     * @param teamName team name, at most sixteen characters
     * @param prefix   team prefix, at most sixteen translated characters
     * @param entries  scoreboard entries that should belong to the team
     */
    public void replaceTeam(
            String teamName,
            String prefix,
            Collection<String> entries
    ) {
        replaceTeam(teamName, prefix, "", entries);
    }

    /**
     * Replaces one team, its prefix, suffix, and entries.
     *
     * @param teamName team name, at most sixteen characters
     * @param prefix   team prefix, at most sixteen translated characters
     * @param suffix   team suffix, at most sixteen translated characters
     * @param entries  scoreboard entries that should belong to the team
     */
    public void replaceTeam(
            String teamName,
            String prefix,
            String suffix,
            Collection<String> entries
    ) {
        ensureOpen();
        Objects.requireNonNull(teamName, "teamName");
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(suffix, "suffix");
        Objects.requireNonNull(entries, "entries");
        for (String entry : entries) Objects.requireNonNull(entry, "entries contains null");
        String translatedPrefix = LegacyText.colorize(prefix);
        String translatedSuffix = LegacyText.colorize(suffix);
        if (teamName.isBlank() || teamName.length() > 16) {
            throw new IllegalArgumentException("Scoreboard teamName must contain 1 to 16 characters");
        }
        if (translatedPrefix.length() > 16) {
            throw new IllegalArgumentException("Scoreboard team prefix cannot exceed 16 characters");
        }
        if (translatedSuffix.length() > 16) {
            throw new IllegalArgumentException("Scoreboard team suffix cannot exceed 16 characters");
        }

        Team team = teams.computeIfAbsent(teamName, scoreboard::registerNewTeam);
        if (!translatedPrefix.equals(team.getPrefix())) team.setPrefix(translatedPrefix);
        if (!translatedSuffix.equals(team.getSuffix())) team.setSuffix(translatedSuffix);
        Set<String> desiredEntries = new HashSet<>(entries);
        for (String currentEntry : new HashSet<>(team.getEntries())) {
            if (!desiredEntries.contains(currentEntry)) team.removeEntry(currentEntry);
        }
        for (String entry : desiredEntries) {
            removeEntryFromOtherTeams(team, entry);
            if (!team.hasEntry(entry)) team.addEntry(entry);
        }
    }

    /**
     * Unregisters every team not present in the supplied retained-name collection.
     *
     * @param retainedTeamNames team names that must remain registered
     */
    public void retainTeams(Collection<String> retainedTeamNames) {
        ensureOpen();
        Objects.requireNonNull(retainedTeamNames, "retainedTeamNames");
        Set<String> retained = new HashSet<>();
        for (String teamName : retainedTeamNames) retained.add(Objects.requireNonNull(teamName, "team name"));
        for (String teamName : new HashSet<>(teams.keySet())) {
            if (!retained.contains(teamName)) removeTeam(teamName);
        }
    }

    /**
     * Changes one owned team's vanilla name-tag visibility.
     *
     * @param teamName   owned team name
     * @param visibility desired visibility status
     */
    public void setTeamNameTagVisibility(String teamName, Team.OptionStatus visibility) {
        ensureOpen();
        Objects.requireNonNull(teamName, "teamName");
        Team.OptionStatus desired = Objects.requireNonNull(visibility, "visibility");
        Team team = teams.get(teamName);
        if (team == null) throw new IllegalArgumentException("Unknown scoreboard team: " + teamName);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, desired);
    }

    /**
     * Unregisters one plugin-owned team.
     *
     * @param teamName team name
     */
    public void removeTeam(String teamName) {
        ensureOpen();
        Objects.requireNonNull(teamName, "teamName");
        Team team = teams.remove(teamName);
        if (team != null) team.unregister();
    }

    /**
     * Removes an entry from every team in this session.
     *
     * @param entry scoreboard entry
     */
    public void removeEntryFromTeams(String entry) {
        ensureOpen();
        Objects.requireNonNull(entry, "entry");
        for (Team team : teams.values()) team.removeEntry(entry);
    }

    /**
     * Removes and unregisters every team owned by this session.
     */
    public void clearTeams() {
        ensureOpen();
        for (Team team : new HashSet<>(teams.values())) team.unregister();
        teams.clear();
    }

    /**
     * Returns the Bukkit scoreboard owned by this session.
     *
     * @return owned scoreboard
     */
    public Scoreboard scoreboard() {
        ensureOpen();
        return scoreboard;
    }

    /**
     * Restores viewers and unregisters all owned Bukkit objects.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (Bukkit.getScoreboardManager() != null) {
            Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Player player : new HashSet<>(viewers)) {
                if (player.isOnline() && player.getScoreboard() == scoreboard) player.setScoreboard(mainScoreboard);
            }
        }
        viewers.clear();
        for (Team team : new HashSet<>(teams.values())) team.unregister();
        teams.clear();
        unregisterBelowNameObjective();
        objective.unregister();
        renderedEntries.clear();
    }

    private void removeEntryFromOtherTeams(Team target, String entry) {
        for (Team team : teams.values()) {
            if (team != target) team.removeEntry(entry);
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Scoreboard session is closed");
    }

    private void unregisterBelowNameObjective() {
        if (belowNameObjective == null) return;
        belowNameObjective.unregister();
        belowNameObjective = null;
        belowNameCriteria = null;
        belowNameScores.clear();
    }
}
