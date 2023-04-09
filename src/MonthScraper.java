import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class MonthScraper {
    private final int SEASON;
    private final ConcurrentHashMap<String, TreeMap<MonthDay, ArrayList<String[]>>> IN_SEASON_TEAM_BIRTHDAYS;


    /**
     * Class constructor
     * @param season                the NBA season, as an <code>int</code> (e.g., 2023)
     * @param inSeasonTeamBirthdays a mapping from each team abbreviation (e.g., "ATL") to
     *                              a corresponding map mapping birthdays to players sharing that birthday
     *                              e.g., for the 2023 season, inSeasonTeamBirthdays.get("DAL") contains:
     *                                  03-16: Reggie Bullock (1991), Tim Hardaway Jr. (1992)
     */
    public MonthScraper(int season,
                        ConcurrentHashMap<String, TreeMap<MonthDay, ArrayList<String[]>>> inSeasonTeamBirthdays) {
        this.SEASON =  season;
        this.IN_SEASON_TEAM_BIRTHDAYS = new ConcurrentHashMap<>(inSeasonTeamBirthdays);
    }


    /**
     * retrieves a player's stat line for a day-after-birthday game that has already occurred
     * @param player    the player's name, as a String, e.g., "Trae Young"
     * @param gameDate  a LocalDate representation of the game date
     * @param homeTeam  a String containing the home team's abbreviation, e.g., "ATL"
     * @return a String representation of the stat line
     * @throws IOException
     * @throws InterruptedException since this scrapes data from basketball-reference.com, we may have to
     *                              <code>Time.pauseExecutionIfNecessary()</code>
     */
    // getStats could be edited to guarantee no more than one connection per game
    // currently, it connects once per birthday boy for a given game
    // however, over the course of the season, there are so few multiple-birthday-boy games
    // that this savings would be minimal
    public String getStats(String player, LocalDate gameDate, String homeTeam)
            throws IOException, InterruptedException {
        StringBuilder statLine = new StringBuilder();

        Time.pauseExecutionIfNecessary();

        URL boxScorePage = new URL("https://www.basketball-reference.com/boxscores/" +
                gameDate.toString().replace("-", "") + "0" +
                homeTeam + ".html");
        HttpURLConnection boxScoreConnection = (HttpURLConnection) boxScorePage.openConnection();
        Time.BRUrlConnectionsThisHour++;
        InputStream boxScoreInputStream = boxScoreConnection.getInputStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(boxScoreInputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(player)) {
                    if (line.contains("Did Not Play")) {
                        statLine.append(" (DNP)");
                        break;
                    }
                    else if (line.contains("Did Not Dress")) {
                        statLine.append(" (DND)");
                        break;
                    }
                    else {
                        statLine.append(" (");
                        int index = line.indexOf("data-stat=\"mp\"");
                        while (line.charAt(index) != '>') {
                            index++;
                        }
                        index++;
                        while (line.charAt(index) != '<') {
                            statLine.append(line.charAt(index));
                            index++;
                        }
                        statLine.append(" mp, ");

                        index = line.indexOf("data-stat=\"pts\"");
                        while (line.charAt(index) != '>') {
                            index++;
                        }
                        index++;
                        while (line.charAt(index) != '<') {
                            statLine.append(line.charAt(index));
                            index++;
                        }
                        statLine.append(" pts, ");

                        index = line.indexOf("data-stat=\"fg\"");
                        while (line.charAt(index) != '>') {
                            index++;
                        }
                        index++;
                        while (line.charAt(index) != '<') {
                            statLine.append(line.charAt(index));
                            index++;
                        }
                        statLine.append("/");

                        index = line.indexOf("data-stat=\"fga\"");
                        while (line.charAt(index) != '>') {
                            index++;
                        }
                        index++;
                        while (line.charAt(index) != '<') {
                            statLine.append(line.charAt(index));
                            index++;
                        }
                        statLine.append(" fga, ");

                        index = line.indexOf("data-stat=\"trb\"");
                        while (line.charAt(index) != '>') {
                            index++;
                        }
                        index++;
                        while (line.charAt(index) != '<') {
                            statLine.append(line.charAt(index));
                            index++;
                        }
                        statLine.append(" reb, ");

                        index = line.indexOf("data-stat=\"ast\"");
                        while (line.charAt(index) != '>') {
                            index++;
                        }
                        index++;
                        while (line.charAt(index) != '<') {
                            statLine.append(line.charAt(index));
                            index++;
                        }
                        statLine.append(" ast)");
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            return "";
        }
        boxScoreConnection.disconnect();
        return statLine.toString();
    }


    /**
     * gets all birthday boys (and their stats, if applicable) for team's game on <code>gameDate</code>
     * @param teamAbbreviation      e.g., "ATL"
     * @param gameDate              a LocalDate representation of the game date
     * @param homeTeamAbbreviation  e.g., "ATL". Note that teamAbbreviation and homeTeamAbbreviation may be equal.
     * @return a String containing all birthday boys (and their stats, if applicable)
     * @throws IOException
     * @throws InterruptedException since this scrapes data from basketball-reference.com, we may have to
     *                              <code>Time.pauseExecutionIfNecessary()</code>
     */
    public String getBirthdays(String teamAbbreviation, LocalDate gameDate, String homeTeamAbbreviation)
            throws IOException, InterruptedException {
        LocalDate localDayBeforeGame = gameDate.minusDays(1);
        MonthDay localMonthDayBeforeGame = java.time.MonthDay.of(localDayBeforeGame.getMonthValue(),
                localDayBeforeGame.getDayOfMonth());

        StringBuilder out = new StringBuilder();

        if (IN_SEASON_TEAM_BIRTHDAYS.get(teamAbbreviation).get(localMonthDayBeforeGame) != null) {
            out.append(teamAbbreviation).append(": ");
            for (int i = 0; i < IN_SEASON_TEAM_BIRTHDAYS.get(teamAbbreviation).get(localMonthDayBeforeGame).size(); i++) {
                int age = localDayBeforeGame.getYear() -
                        Integer.parseInt(IN_SEASON_TEAM_BIRTHDAYS.get(teamAbbreviation)
                                .get(localMonthDayBeforeGame).get(i)[1]);
                String turn = " turns ";
                String stats = "";
                if (java.time.LocalDate.now().isAfter(gameDate)) {
                    turn = " turned ";
                    stats = getStats(IN_SEASON_TEAM_BIRTHDAYS
                                    .get(teamAbbreviation)
                                    .get(localMonthDayBeforeGame)
                                    .get(i)[0],
                            gameDate,
                            homeTeamAbbreviation);
                }
                out.append(IN_SEASON_TEAM_BIRTHDAYS.get(teamAbbreviation).get(localMonthDayBeforeGame).get(i)[0])
                        .append(turn).append(age).append(stats);
                if (i != IN_SEASON_TEAM_BIRTHDAYS.get(teamAbbreviation).get(localMonthDayBeforeGame).size() - 1) {
                    out.append(", ");
                }
            }
            out.append(System.lineSeparator());
        }
        return out.toString();
    }


    /**
     * returns a string containing the contents to be written to the month file
     * @param monthNumber           e.g., 10 for "October"
     * @throws IOException
     * @throws InterruptedException since this scrapes data from basketball-reference.com, we may have to
     *                              <code>Time.pauseExecutionIfNecessary()</code>
     * @return                      a <code>String</code> containing every birthday game (and birthday boy results)
     *                              in the month
     */
    public String getMonthFileContents(int monthNumber) throws IOException, InterruptedException {
        Time.pauseExecutionIfNecessary();

        // Make a URL to the web page
        URL schedulePage = new URL("https://www.basketball-reference.com/leagues/NBA_" +
                SEASON + "_games-" +
                Nba.MONTHS.get(monthNumber).toLowerCase() +
                ".html#schedule");

        // Get the input stream through URL Connection
        HttpURLConnection scheduleConnection = (HttpURLConnection) schedulePage.openConnection();
        Time.BRUrlConnectionsThisHour++;
        InputStream scheduleInputStream = scheduleConnection.getInputStream();

        StringBuilder fileContents = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(scheduleInputStream))) {
            String line;
            boolean colgroup = false;
            int tbodyCount = 0;
            boolean endTable = false;
            boolean inTable;
            // read each line
            while ((line = br.readLine()) != null) {
                if (line.contains("colgroup")) {
                    colgroup = true;
                }
                if (line.contains("tbody")) {
                    tbodyCount++;
                }
                if (colgroup && (tbodyCount >= 2)) {
                    if (line.contains("</table>")) {
                        endTable = true;
                    }
                }
                inTable = colgroup && (tbodyCount >= 2) && !endTable;

                // iterate through the list of games in that month
                if (inTable && line.length() > 0) {
                    // get day of the month
                    int dayStartIndex = line.indexOf("day=") + 4;
                    int dayEndIndex = dayStartIndex + 1;
                    while (line.charAt(dayEndIndex) != '&') {
                        dayEndIndex++;
                    }
                    int day = Integer.parseInt(line.substring(dayStartIndex, dayEndIndex));

                    // set gameDate
                    int year = SEASON;
                    if (monthNumber > 6) {
                        year--;
                    }
                    LocalDate gameDate = java.time.LocalDate.of(year, monthNumber, day);

                    // get teams who played in that game: visitor and home
                    int teamIndexStart = line.indexOf("visitor_team_name");
                    while (!Character.isUpperCase(line.charAt(teamIndexStart))) {
                        teamIndexStart++;
                    }
                    String visitor = line.substring(teamIndexStart, teamIndexStart + 3);
                    teamIndexStart += 3;
                    while (!Character.isUpperCase(line.charAt(teamIndexStart))) {
                        teamIndexStart++;
                    }
                    String home = line.substring(teamIndexStart, teamIndexStart + 3);

                    // birthdate search will be on day before gameDate
                    LocalDate dayBeforeGame = gameDate.minusDays(1);
                    MonthDay monthDayBeforeGame = java.time.MonthDay.of(dayBeforeGame.getMonthValue(),
                            dayBeforeGame.getDayOfMonth());

                    if (IN_SEASON_TEAM_BIRTHDAYS.get(visitor).get(monthDayBeforeGame) != null ||
                            IN_SEASON_TEAM_BIRTHDAYS.get(home).get(monthDayBeforeGame) != null) {
                        fileContents.append(System.lineSeparator())
                                .append(day).append(": ")
                                .append(visitor).append(" at ").append(home);

                        // check whether the game is in the past
                        if (java.time.LocalDate.now().isAfter(gameDate)) {
                            String score = "";
                            // if it is, get the game score
                            int scoreIndexStart = line.indexOf("visitor_pts");
                            while (line.charAt(scoreIndexStart) != '>') {
                                scoreIndexStart++;
                            }
                            scoreIndexStart++;
                            int scoreIndexStop = scoreIndexStart;
                            while (line.charAt(scoreIndexStop) != '<') {
                                scoreIndexStop++;
                            }
                            score += line.substring(scoreIndexStart, scoreIndexStop) + "-";

                            scoreIndexStart = line.indexOf("home_pts");
                            while (line.charAt(scoreIndexStart) != '>') {
                                scoreIndexStart++;
                            }
                            scoreIndexStart++;
                            scoreIndexStop = scoreIndexStart;
                            while (line.charAt(scoreIndexStop) != '<') {
                                scoreIndexStop++;
                            }
                            score += line.substring(scoreIndexStart, scoreIndexStop);
                            // System.out.print(", " + score);
                            fileContents.append(", ").append(score);
                        }
                        fileContents.append(System.lineSeparator());

                        // print all visitor team birthdays
                        fileContents.append(getBirthdays(visitor, gameDate, home));
                        // print all home team birthdays
                        fileContents.append(getBirthdays(home, gameDate, home));
                    }
                }
            }
        }
        scheduleConnection.disconnect();
        return fileContents.toString();
    }


    /**
     * creates a .txt file for monthNumber containing all birthday games
     * @param monthNumber           e.g., 10 for "October"
     * @throws InterruptedException since this scrapes data from basketball-reference.com, we may have to
     *                              <code>Time.pauseExecutionIfNecessary()</code>
     */
    public void makeNewMonthFile(int monthNumber) throws InterruptedException {
        String path = "Season" + SEASON + "/" + Nba.MONTHS.get(monthNumber) + ".txt";
        try {
            File file = new File(path);

            // check whether the file already exists
            // if not, create it and write all the birthdays to it
            if (!file.exists()) {
                file.createNewFile();
                FileWriter fw = new FileWriter(file);
                PrintWriter pw = new PrintWriter(fw);
                // clear any old contents of the file before writing updated fileContents
                pw.flush();
                pw.write(getMonthFileContents(monthNumber));
                pw.close();
                fw.close();
                System.out.println("Created " + path);
            }
            // if the .txt file already exists...
            else {
                int year = SEASON;
                if (monthNumber == 10 || monthNumber == 11) {
                    year--;
                }
                // check the last time the .txt file was modified
                // if it was before the end of the month, overwrite its contents
                if (java.time.LocalDate.ofEpochDay(file.lastModified() / (long) (1000 * 60 * 60 * 24))
                        .isBefore(java.time.LocalDate.of(year, monthNumber % 12 + 1, 1))) {
                    FileWriter fw = new FileWriter(file);
                    PrintWriter pw = new PrintWriter(fw);
                    // clear any old contents of the file before writing updated fileContents
                    pw.flush();
                    pw.write(getMonthFileContents(monthNumber));
                    pw.close();
                    fw.close();
                    System.out.println("Created " + path);
                }
                // otherwise, do nothing
                // (note: better to not update once month has ended, not least because
                // rosters may have changed since then, which would introduce errors in our data)
                else {
                    System.out.println(path + " already existed and had up-to-date information. No changes made.");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}