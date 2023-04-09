import java.io.*;
import java.time.MonthDay;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Nba {
    // store MONTHS in LinkedHashMap to keep entries in insertion order
    public static final LinkedHashMap<Integer, String> MONTHS = new LinkedHashMap<>() {{
        put(10, "October");
        put(11, "November");
        put(12, "December");
        put(1, "January");
        put(2, "February");
        put(3, "March");
        put(4, "April");
//        put(5, "May");
//        put(6, "June");
    }};

    public static final LinkedHashMap<String, String> TEAMS = new LinkedHashMap<>() {{
        put("ATL", "Atlanta Hawks");
        put("BOS", "Boston Celtics");
        put("BRK", "Brooklyn Nets");
        put("CHO", "Charlotte Hornets");
        put("CHI", "Chicago Bulls");
        put("CLE", "Cleveland Cavaliers");
        put("DAL", "Dallas Mavericks");
        put("DEN", "Denver Nuggets");
        put("DET", "Detroit Pistons");
        put("GSW", "Golden State Warriors");
        put("HOU", "Houston Rockets");
        put("IND", "Indiana Pacers");
        put("LAC", "Los Angeles Clippers");
        put("LAL", "Los Angeles Lakers");
        put("MEM", "Memphis Grizzlies");
        put("MIA", "Miami Heat");
        put("MIL", "Milwaukee Bucks");
        put("MIN", "Minnesota Timberwolves");
        put("NOP", "New Orleans Pelicans");
        put("NYK", "New York Knicks");
        put("OKC", "Oklahoma City Thunder");
        put("ORL", "Orlando Magic");
        put("PHI", "Philadelphia 76ers");
        put("PHO", "Phoenix Suns");
        put("POR", "Portland Trail Blazers");
        put("SAC", "Sacramento Kings");
        put("SAS", "San Antonio Spurs");
        put("TOR", "Toronto Raptors");
        put("UTA", "Utah Jazz");
        put("WAS", "Washington Wizards");
    }};
    private final int SEASON;


    /**
     * inSeasonTeamBirthdays will map each team abbreviation (e.g., "ATL") to a corresponding map mapping
     * birthdays to players sharing that birthday
     * e.g., inSeasonTeamBirthdays.get("DAL") contains 03-16: Reggie Bullock (1991), Tim Hardaway Jr. (1992)
     */
    private ConcurrentHashMap<String, TreeMap<MonthDay, ArrayList<String[]>>> inSeasonTeamBirthdays;


    /**
     * Default class constructor
     * sets <code>SEASON</code> to the current NBA season, as determined by <code>Time.today</code>
     */
    public Nba() {
        SEASON = getCurrentSeason();
    }


    /**
     * Initialization constructor
     * @param season the NBA season in question (note: the 22-23 season, for example, is referred to as
     *               the 2023 NBA season)
     */
    public Nba(int season) {
        this.SEASON = season;
    }

    public static int getCurrentSeason() {
        if (Time.today.getMonthValue() < 10) {
            return Time.today.getYear();
        }
        else {
            return Time.today.getYear() + 1;
        }
    }

    private void printWelcome() {
        System.out.println("Welcome to the " + SEASON + " NBA season.");
    }


    /**
     * Gets the locally saved team roster file closes to the game <code>month</code> in question,
     * so that the roster is as accurate as possible
     * Note: if the closest available rosters are equidistant from the game <code>month</code>,
     * this method selects the roster acquired prior to the game rather than the roster acquired
     * after the game.
     * @param team  a <code>String</code> representation of the team abbreviation, e.g., "ATL"
     * @param month the month in which the game was played, as an <code>int</code>
     * @return the  <code>File</code> containing the best available locally saved team roster
     */
    public File findClosestRoster(String team, int month) {
        for (int i = 0; i < 12; i++) {
            int monthNumber = (month + (int) (Math.pow(-1, i)) * (i + 1) / 2) % 12;
            if (monthNumber == 0) {
                monthNumber = 12;
            }
            String path = "Season" + SEASON + "/TeamRosters/" + team + monthNumber + ".txt";
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }


    /**
     * sets the TreeMap<MonthDay, ArrayList<String[]>> inSeasonTeamBirthdays instance variable
     * (where the ArrayList<String[]> is a list of playerName, birthYear tuples)
     * to be the value for each team abbreviation (e.g., "ATL") key
     */
    public void setInSeasonTeamBirthdays(int month) {
        TEAMS.keySet().parallelStream().forEach( (team) -> {
            Scanner trScanner;
            try {
                trScanner = new Scanner(findClosestRoster(team, month));
            }
            catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            // go through the Roster html table
            while (trScanner.hasNextLine()) {
                String line = trScanner.nextLine();
                int index = line.indexOf("birth_date") + "birth_date\" csk=\"20001211\" >".length();
                StringBuilder birthdayString = new StringBuilder();
                // get player's birthday
                while (line.charAt(index) != '<') {
                    birthdayString.append(line.charAt(index));
                    index++;
                }
                //System.out.println("birthdayString = " + birthdayString);
                String[] monthDayYear = birthdayString.toString().split(" ");

                String birthMonth = monthDayYear[0];
                //System.out.println("birthMonth = " + birthMonth);

                // only select birthdays that occur during the season
                if (MONTHS.containsValue(birthMonth)) {
                    int birthMonthNumber = 0;
                    for (Map.Entry<Integer, String> entry : MONTHS.entrySet()) {
                        if (entry.getValue().equals(birthMonth)) {
                            birthMonthNumber = entry.getKey();
                            //System.out.println("birthMonthNumber = " + birthMonthNumber);
                        }
                    }
                    monthDayYear[1] = monthDayYear[1].substring(0,monthDayYear[1].length()-1);
                    int birthDay = Integer.parseInt(monthDayYear[1]);
                    //System.out.println("birthDay = " + birthDay);

                    MonthDay birthMonthDay = java.time.MonthDay.of(birthMonthNumber, birthDay);

                    String birthYear = monthDayYear[2];
                    //System.out.println("birthYear = " + birthYear);

                    // get player name
                    StringBuilder player = new StringBuilder();
                    index = line.indexOf(".html") + ".html".length() + 2;
                    while (line.charAt(index) != '<') {
                        player.append(line.charAt(index));
                        index++;
                    }
                    String[] newPlayerEntry = new String[]{player.toString(), birthYear};

                    // if team already has a birthday TreeMap, and it contains this birthMonthDay key,
                    // then we needn't create a new ArrayList
                    if (inSeasonTeamBirthdays.get(team) != null && inSeasonTeamBirthdays.get(team).containsKey(birthMonthDay)) {
                        // add the player to the (shared) birthMonthDay
                        inSeasonTeamBirthdays.get(team).get(birthMonthDay).add(newPlayerEntry);
                        // sort the players sharing the same birthMonthDay alphabetically
                        // https://stackoverflow.com/questions/4699807/sort-arraylist-of-array-in-java
                        inSeasonTeamBirthdays.get(team).get(birthMonthDay).sort(
                                Comparator.comparing(playerEntry -> playerEntry[0])
                        );
                    }
                    // otherwise, either the team has no birthday TreeMap yet, or this is a new birthMonthDay
                    else {
                        // either way, we need a new ArrayList containing our player
                        ArrayList<String[]> newPlayerList = new ArrayList<>();
                        newPlayerList.add(newPlayerEntry);
                        // if the team has no birthday TreeMap, create one with this player's birthDayMonth mapping to our newPlayerList
                        if (inSeasonTeamBirthdays.get(team) == null) {
                            inSeasonTeamBirthdays.put(team, new TreeMap<>(
                                    // set comparator to order birthMonthDays starting with season start, rather than Jan. 1
                                    (o1, o2) -> {
                                        int i1 = o1.getDayOfMonth();
                                        if (o1.getMonthValue() > 6) {
                                            i1 += o1.getMonthValue() * 100;
                                        } else {
                                            i1 += o1.getMonthValue() * 10000;
                                        }

                                        int i2 = o2.getDayOfMonth();
                                        if (o2.getMonthValue() > 6) {
                                            i2 += o2.getMonthValue() * 100;
                                        } else {
                                            i2 += o2.getMonthValue() * 10000;
                                        }
                                        return i1 - i2;
                                    }
                            ) {{
                                put(birthMonthDay, newPlayerList);
                            }});
                        }
                        // otherwise, add this birthdayMonth-newPlayerList mapping to the existing TreeMap
                        else {
                            inSeasonTeamBirthdays.get(team).put(birthMonthDay, newPlayerList);
                        }
                    }
                }
            }
            trScanner.close();
        });
    }


    /**
     * accessor method to retrieve a list of players who have birthdays during the NBA season for a given team
     * @param teamAbbreviation  e.g., "ATL"
     * @return the list of players as a String
     */
    public String inSeasonTeamBirthdaysToString(String teamAbbreviation) {
        StringBuilder str = new StringBuilder(teamAbbreviation + " birthdays:" + System.lineSeparator());
        // loop through a team's list of birthMonthDays (in season)
        for (Map.Entry<MonthDay, ArrayList<String[]>> entry : inSeasonTeamBirthdays.get(teamAbbreviation).entrySet()) {
            // add the MonthDay to str in the form MM-DD
            str.append(entry.getKey().toString().substring(2)).append(": ");
            for (int i=0; i < entry.getValue().size() - 1; i++) {
                // add "playerName (birthYear), " to str
                str.append(entry.getValue().get(i)[0]).append(" (").append(entry.getValue().get(i)[1]).append("), ");
            }
            // add the last "playerName (birthYear)" to str, and start a new line
            str.append(entry.getValue().get(entry.getValue().size() - 1)[0])
                    .append(" (")
                    .append(entry.getValue().get(entry.getValue().size() - 1)[1])
                    .append(")").append(System.lineSeparator());
        }
        return str.toString();
    }


    /**
     * create AllInSeasonBirthdaysAllTeams.txt, which prints inSeasonTeamBirthdaysToString for every team
     */
    public void makeAllInSeasonBirthdaysAllTeamsFile() {
        // first setInSeasonTeamBirthdays such that it uses the most current rosters available
        ArrayList<Integer> months = new ArrayList<>(Nba.MONTHS.keySet());
        setInSeasonTeamBirthdays(months.get(months.size() - 1));

        String path = "Season" + SEASON + "/" + "AllInSeasonBirthdaysAllTeams.txt";
        try {
            File file = new File(path);

            // check whether the file already exists
            // if not, create it and write all the birthdays to it
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file);
            PrintWriter pw = new PrintWriter(fw);
            // clear any old contents of the file before writing updated fileContents
            pw.flush();
            for (String team : TEAMS.keySet()) {
                pw.write(inSeasonTeamBirthdaysToString(team) + System.lineSeparator());
            }
            pw.close();
            fw.close();

            System.out.println("You now see every in-season birthday " +
                    "(according the latest rosters available) at " +
                    path);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * This is the workhorse method that:
     *      checks which month files are already locally saved,
     *      checks which team files (rosters) are already locally saved,
     *      saves new team roster files if necessary/desired,
     *      determines which month files to write,
     *      writes all those files using the best available team roster data,
     *      make a new file containing all in-season birthdays for all teams, and
     *      saves birthday-game statistics to a local file.
     * @throws InterruptedException since this scrapes data from basketball-reference.com, we may have to
     *                              <code>Time.pauseExecutionIfNecessary()</code>
     */
    public void run() throws InterruptedException {
        printWelcome();
        inSeasonTeamBirthdays = new ConcurrentHashMap<>();

        // check which month files are already locally saved
        MonthChecker mc = new MonthChecker(SEASON);
        mc.setMissingMonths();
        mc.setPartialMonths();
        mc.setCompletedMonths();
        mc.printCompletedMonths();

        // check which team files (rosters) are already locally saved
        TeamChecker tc = new TeamChecker(SEASON);
        tc.setAllTeamRosters();
        tc.setNewWebScrapeNeeded();

        // save new team roster files if necessary/desired
        if (tc.isNewWebScrapeNeeded()) {
            TeamScraper ts = new TeamScraper(SEASON);
            ts.makeNewTeamRosterFiles();
        }

        // determine which month files to write (or overwrite)
        mc.setMonthsToFetch();

        if(!mc.getMonthsToFetch().isEmpty()) {
            // write all those files, using the best available team roster data
            for (int month : mc.getMonthsToFetch()) {
                setInSeasonTeamBirthdays(month);
                MonthScraper ms = new MonthScraper(SEASON, inSeasonTeamBirthdays);
                ms.makeNewMonthFile(month);
            }
            System.out.println("Done.");
        }

        // make a new file containing all in-season birthdays for all teams
        makeAllInSeasonBirthdaysAllTeamsFile();

        // collect and locally save birthday-game statistics to a file
        Statistics stats = new Statistics(SEASON);
        stats.run();
    }
}