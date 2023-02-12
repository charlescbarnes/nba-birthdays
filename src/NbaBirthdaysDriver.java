import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NbaBirthdaysDriver {
    // store MONTHS in LinkedHashMap to keep entries in insertion order
    private static final LinkedHashMap<Integer, String> MONTHS = new LinkedHashMap<>() {{
        put(10, "October");
        put(11, "November");
        put(12, "December");
        put(1, "January");
        put(2, "February");
        put(3, "March");
        put(4, "April");
    }};

    private static int season;

    private static final LinkedHashMap<String, String> TEAMS = new LinkedHashMap<>() {{
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

    /**
     * teamRosterPageContents will map each team abbreviation (e.g., "ATL") to the HTML code for that team's
     * roster table on basketball-reference.com
     */
    // static object used in parallelStream in other methods (setTeamRosterPageContents() and setInSeasonTeamBirthdays())
    // HashMap is not concurrent safe; thus the use of ConcurrentHashMap
    private static ConcurrentHashMap<String, String> teamRosterPageContents = new ConcurrentHashMap<>();

    /**
     * inSeasonTeamBirthdays will map each team abbreviation (e.g., "ATL") to a corresponding map mapping
     * birthdays to players sharing that birthday
     * e.g., inSeasonTeamBirthdays.get("DAL") contains 03-16: Reggie Bullock (1991), Tim Hardaway Jr. (1992)
     */
    private static ConcurrentHashMap<String, TreeMap<MonthDay, ArrayList<String[]>>> inSeasonTeamBirthdays = new ConcurrentHashMap<>();

    /**
     * basketball-reference.com throws a 429 error when you exceed 30 requests in an hour
     * this instance variable is a counter used to pauseExecutionIfNecessary() to avoid interruptions
     */
    private static int BRUrlConnectionsThisHour = 0;

    public static void main(String[] args) throws IOException, InterruptedException {
        // set season to this NBA season
        LocalDate todaysDate = java.time.LocalDate.now();
        setSeason(todaysDate);
        System.out.println("Welcome to the " + season + " NBA season.");

        Scanner scan = new Scanner(System.in);

        LinkedList<Integer> listOfMonthsWithMissingData = new LinkedList<>();

        // check to see if there's a folder for this season
        File seasonFolder = new File("Season" + String.valueOf(season));
        // if there isn't, make one
        if (!seasonFolder.exists()) {
            seasonFolder.mkdir();
            // if there wasn't already a folder, we know data for all MONTHS is missing
            listOfMonthsWithMissingData = (LinkedList<Integer>) MONTHS.keySet();
        }
        else {
            // otherwise, for each month...
            for (Integer monthNumber : MONTHS.keySet()) {
                String path = "Season" + season + "/" + MONTHS.get(monthNumber) + ".txt";
                try {
                    File file = new File(path);

                    // check whether the birthday-game file already exists
                    if (file.exists()) {
                        int year = season;
                        if (monthNumber == 10 || monthNumber == 11) {
                            year--;
                        }
                        // check the date the .txt file was last modified
                        // if it was before the end of the month, then add that month is missing data--add it to the list
                        if (java.time.LocalDate.ofEpochDay(file.lastModified() / Long.valueOf(1000 * 60 * 60 * 24))
                                .isBefore(java.time.LocalDate.of(year, monthNumber % 12 + 1, 1))) {
                            listOfMonthsWithMissingData.add(monthNumber);
                        }
                    }
                    // if the birthday-game file doesn't exist, add it to the list
                    else {
                        listOfMonthsWithMissingData.add(monthNumber);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // if user already has some complete month files saved, let them know
        if (MONTHS.keySet().size() > listOfMonthsWithMissingData.size()) {
            System.out.println("You appear to have complete birthday-game data for:");
            for (Integer monthNumber : MONTHS.keySet()) {
                if (!listOfMonthsWithMissingData.contains(monthNumber)) {
                    System.out.println(" - " + MONTHS.get(monthNumber));
                }
            }
        }

        // if the user has some incomplete month files, then...
        if (listOfMonthsWithMissingData.size() > 0) {
            // determine whether to retrieve new roster data from basketball-reference.com or use the data already saved
            boolean retrieveNewRosters = false;
            LocalDate earliestFileModificationDate = todaysDate;
            for (String team : TEAMS.keySet()) {
                File file = new File("Season" + season + "/" + team + ".txt");
                // if user currently has no roster data, it needs to be retrieved
                if (!file.exists()) {
                    retrieveNewRosters = true;
                    System.out.println("I need to retrieve new roster data from basketball-reference.com.");
                    System.out.println("This might add about an hour to program execution.");
                    break;
                }
                // otherwise, determine when user's roster data was last updated
                else {
                    if (java.time.LocalDate.ofEpochDay(file.lastModified() / Long.valueOf(1000 * 60 * 60 * 24))
                            .isBefore(earliestFileModificationDate)) {
                        earliestFileModificationDate = java.time.LocalDate.ofEpochDay(file.lastModified() / Long.valueOf(1000 * 60 * 60 * 24));
                    }
                }
            }

            // if user already has some roster data, prompt them to either:
            // use the data they already have, or
            // update that data by retrieving current information from basketball-reference.com
            if (!retrieveNewRosters) {
                System.out.println("Your team roster data was last retrieved on " + earliestFileModificationDate + ".");
                System.out.println("Do you want me to retrieve updated data from basketball-reference.com?");
                System.out.println("(Note: Doing so will likely add an hour to program execution.)");
                System.out.print("Enter \"Y\" or \"N\": ");

                String answer = scan.next().trim();

                while (!answer.equals("Y") && !answer.equals("N")) {
                    System.out.print("Please enter \"Y\" or \"N\": ");
                    answer = scan.next();
                }
                if (answer.equals("Y")) {
                    retrieveNewRosters = true;
                }
            }

            setTeamRosterPageContents(retrieveNewRosters);
            setInSeasonTeamBirthdays();
            makeAllInSeasonBirthdaysAllTeamsFile(retrieveNewRosters);

            System.out.println("Which months' birthday-games do you want me to retrieve?");
            System.out.println(" - For all missing months, enter \"0\".");
            System.out.print(" - For just one month, enter the month number (");
            // list all monthNumber-month pairs for months with missing data
            for (int i = 0; i < listOfMonthsWithMissingData.size(); i++) {
                System.out.print("\"" + listOfMonthsWithMissingData.get(i) + "\" for " +
                        MONTHS.get(listOfMonthsWithMissingData.get(i)));
                if (i != listOfMonthsWithMissingData.size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println(").");
            System.out.println(" - To not run any new months, enter \"100\".");
            System.out.print("Month number: ");

            String monthSelectionString = "";
            boolean monthSelectionValid = false;
            int monthSelection = -1;
            while (!monthSelectionValid) {
                monthSelectionString = scan.next().trim();
                // check whether the user entered a valid option. If not, repeat prompt.
                try {
                    monthSelection = Integer.valueOf(monthSelectionString);
                    if (monthSelection == 0 || listOfMonthsWithMissingData.contains(monthSelection) ||
                    monthSelection == 100) {
                        monthSelectionValid = true;
                    }
                    else {
                        System.out.print("Please enter a valid (see above) month number: ");
                    }
                } catch (NumberFormatException e) {
                    System.out.print("Please enter a valid (see above) month number: ");
                }
            }

            // add selected months to listOfMonthsToRun
            LinkedList<Integer> listOfMonthsToRun = new LinkedList<>();
            if (monthSelection == 0) {
                listOfMonthsToRun = listOfMonthsWithMissingData;
                System.out.println("Got it. I'll get birthday-games for the rest of the season for you.");
            }
            else if (listOfMonthsWithMissingData.contains(monthSelection)) {
                listOfMonthsToRun.add(monthSelection);
                System.out.println(MONTHS.get(monthSelection) + " birthday-games, coming right up!");
            }

            // generate files for all months in listOfMonthsToRun
            for (int monthNumber : listOfMonthsToRun) {
                setBirthdayGamesInMonthFile(monthNumber);
            }
            System.out.println("Done.");
        }
        // if there are no months in listOfMonthsWithMissingData, let them know there's no more data to request.
        else {
            System.out.println("You appear to already have complete birthday-game data for the whole " + season +
                    " season.");
        }

        // Statistics
        System.out.println("Do you want me to gather birthday-game stats?");
        System.out.print("Enter \"Y\" or \"N\": ");

        String answer = scan.next().trim();

        while (!answer.equals("Y") && !answer.equals("N")) {
            System.out.print("Please enter \"Y\" or \"N\": ");
            answer = scan.next();
        }
        if (answer.equals("Y")) {
            makeStatisticsFile(todaysDate);
        }

        System.out.println("This is all I have to offer for now. Enjoy!");
    }

    /**
     * sets the instance variable season to the current NBA season, based on:
     * @param todaysDate    a LocalDate representation of today's date
     */
    public static void setSeason(LocalDate todaysDate){
        if (todaysDate.getMonthValue() < 10) {
            season = todaysDate.getYear();
        }
        else {
            season = todaysDate.getYear() + 1;
        }
    }

    /**
     * sets the Roster table HTML (containing birthdays) from basketball-reference.com to be the value
     * for each team abbreviation key in teamRosterPageContents instance variable
     * @param fromWeb If fromWeb is true, current data is scraped from basketball-reference.com
     *                  and saved to local .txt files.
     *                If fromWeb is false, we use the data from the last time we scraped from b-r.com that is saved
     *                  locally.
     */
    public static void setTeamRosterPageContents(boolean fromWeb) throws IOException {
        if (fromWeb) {
            System.out.println("Gathering roster data...");
        }

        // should create a separate thread for each team
        TEAMS.keySet().parallelStream().forEach( (team) -> {
            String pageContents = "";

            // if I want the most current data fetched from the Web
            if (fromWeb) {
                try {
                    pauseExecutionIfNecessary();

                    URL teamPage = new URL("https://www.basketball-reference.com/teams/" + team +
                            "/" + season + ".html#roster");

                    // using HttpURLConnection so that I can .disconnect() when done
                    HttpURLConnection teamConnection = (HttpURLConnection) teamPage.openConnection();
                    BRUrlConnectionsThisHour++;
                    InputStream teamInputStream = teamConnection.getInputStream();

                    try (BufferedReader br = new BufferedReader(new InputStreamReader(teamInputStream))) {
                        String line = null;
                        // read each line
                        while ((line = br.readLine()) != null) {
                            // checks if line is part of Roster table (contains birthday)
                            if (line.contains("<tr") && line.contains("birth_date")) {

                                pageContents += line + System.lineSeparator();
                            }
                        }
                    }
                    // write pageContents to .txt file so that local .txt files are current
                    // as of last fromWeb setTeamRosterPageContents call
                    makeTeamRosterPageContentsFile(team, pageContents);
                    teamInputStream.close();
                    teamConnection.disconnect();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            // if I don't want data fetched from Web, but rather from local .txt files
            else {
                try {
                    pageContents = Files.readString(Path.of("Season" + season + "/" + team + ".txt"), Charset.defaultCharset());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            // set the Roster table HTML to be the value for each team key
            teamRosterPageContents.put(team, pageContents);
        });
        System.out.println("Okay, rosters are set.");
    }

    /**
     * writes the Roster table HTML (containing birthdays) from basketball-reference.com to local .txt files
     * @param teamAbbreviation  e.g., "ATL"
     * @param pageContents      a String representation of the Roster table HTML
     */
    public static void makeTeamRosterPageContentsFile(String teamAbbreviation, String pageContents) {
        String path = "Season" + season + "/" + teamAbbreviation + ".txt";
        try {
            File file = new File(path);

            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(path);
            PrintWriter pw = new PrintWriter(fw);
            // clear any old contents of the file before writing updated pageContents
            pw.flush();
            pw.write(pageContents);
            pw.close();
            fw.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    /**
     * sets the TreeMap<MonthDay, ArrayList<String[]>> inSeasonTeamBirthdays instance variable
     * (where the ArrayList<String[]> is a list of playerName, birthYear tuples)
     * to be the value for each team abbreviation (e.g., "ATL") key
     */
    public static void setInSeasonTeamBirthdays(){
        TEAMS.keySet().parallelStream().forEach( (team) -> {
            Scanner trScanner = new Scanner(teamRosterPageContents.get(team));
            // go through the Roster html table
            while (trScanner.hasNextLine()) {
                String line = trScanner.nextLine();
                int index = line.indexOf("birth_date") + "birth_date\" csk=\"20001211\" >".length();
                String birthdayString = "";
                // get player's birthday
                while (!line.substring(index, index+1).equals("<")){
                    birthdayString += line.substring(index, index+1);
                    index++;
                }
                //System.out.println("birthdayString = " + birthdayString);
                String[] monthDayYear = birthdayString.split(" ");

                String birthMonth = monthDayYear[0];
                //System.out.println("birthMonth = " + birthMonth);

                // only select birthdays that occur during the season
                if (MONTHS.containsValue(birthMonth)){
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
                    String player = "";
                    index = line.indexOf(".html'>") + ".html'>".length();
                    while (!line.substring(index, index+1).equals("<")){
                        player += line.substring(index, index+1);
                        index++;
                    }
                    //System.out.println("player = " + player);

                    String[] newPlayerEntry = new String[]{player, birthYear};

                    // if team already has a birthday TreeMap and it contains this birthMonthDay key,
                    // then we needn't create a new ArrayList
                    if (inSeasonTeamBirthdays.get(team) != null && inSeasonTeamBirthdays.get(team).keySet().contains(birthMonthDay)) {
                        // add the player to the (shared) birthMonthDay
                        inSeasonTeamBirthdays.get(team).get(birthMonthDay).add(newPlayerEntry);
                        // sort the players sharing the same birthMonthDay alphabetically
                        // https://stackoverflow.com/questions/4699807/sort-arraylist-of-array-in-java
                        Collections.sort(inSeasonTeamBirthdays.get(team).get(birthMonthDay), new Comparator<String[]>() {
                            public int compare(String[] playerEntry1, String[] playerEntry2) {
                                return playerEntry1[0].compareTo(playerEntry2[0]);
                            }
                        });
                    }
                    // otherwise, either the team has no birthday TreeMap yet, or this is a new birthMonthDay
                    else {
                        // either way, we need a new ArrayList containing our player
                        ArrayList<String[]> newPlayerList = new ArrayList<>();
                        newPlayerList.add(newPlayerEntry);
                        // if the team has no birthday TreeMap, create one with this player's birthDayMonth mapping to our newPlayerList
                        if (inSeasonTeamBirthdays.get(team) == null) {
                            inSeasonTeamBirthdays.put(team, new TreeMap<MonthDay, ArrayList<String[]>>(
                                    // set comparator to order birthMonthDays starting with season start, rather than Jan. 1
                                    new Comparator<MonthDay>() {
                                        @Override
                                        public int compare(MonthDay o1, MonthDay o2) {
                                            int i1 = o1.getDayOfMonth();
                                            if (o1.getMonthValue() > 6) {
                                                i1 += o1.getMonthValue() * 100;
                                            }
                                            else {
                                                i1 += o1.getMonthValue() * 10000;
                                            }

                                            int i2 = o2.getDayOfMonth();
                                            if (o2.getMonthValue() > 6) {
                                                i2 += o2.getMonthValue() * 100;
                                            }
                                            else {
                                                i2 += o2.getMonthValue() * 10000;
                                            }
                                            return i1-i2;
                                        }
                                    }
                            ){{
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
    public static String inSeasonTeamBirthdaysToString(String teamAbbreviation){
        String str = teamAbbreviation + " birthdays:" + System.lineSeparator();
        // loop through a team's list of birthMonthDays (in season)
        for (Map.Entry<MonthDay, ArrayList<String[]>> entry : inSeasonTeamBirthdays.get(teamAbbreviation).entrySet()) {
            // add the MonthDay to str in the form MM-DD
            str += entry.getKey().toString().substring(2) + ": ";
            for (int i=0; i < entry.getValue().size() - 1; i++){
                // add "playerName (birthYear), " to str
                str += entry.getValue().get(i)[0] + " (" + entry.getValue().get(i)[1] +"), ";
            }
            // add the last "playerName (birthYear)" to str, and start a new line
            str += entry.getValue().get(entry.getValue().size() - 1)[0] + " (" +
                    entry.getValue().get(entry.getValue().size() - 1)[1] +")" + System.lineSeparator();
        };
        return str;
    }

    /**
     * create AllInSeasonBirthdaysAllTeams.txt, which prints inSeasonTeamBirthdaysToString for every team
     * @param overWrite if overWrite is true, overwrite the existing version of this file
     */
    public static void makeAllInSeasonBirthdaysAllTeamsFile(boolean overWrite) {
        String path = "Season" + season + "/" + "AllInSeasonBirthdaysAllTeams.txt";
        try {
            File file = new File(path);

            // check whether the file already exists
            // if not, create it and write all the birthdays to it
            if (!file.exists()) {
                file.createNewFile();
                overWrite = true;
            }

            if (overWrite) {
                FileWriter fw = new FileWriter(file);
                PrintWriter pw = new PrintWriter(fw);
                // clear any old contents of the file before writing updated fileContents
                pw.flush();
                for (String team : TEAMS.keySet()) {
                    pw.write(inSeasonTeamBirthdaysToString(team) + System.lineSeparator());
                }
                pw.close();
                fw.close();
            }

            System.out.println("You now see every in-season birthday at " + path);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * retrieves a player's stat line for a day-after-birthday game that has already occurred
     * @param player    the player's name, as a String, e.g., "Trae Young"
     * @param gameDate  a LocalDate represetation of the game date
     * @param homeTeam  a String containing the home team's abbreviation, e.g., "ATL"
     * @return a String representation of the stat line
     * @throws IOException
     * @throws InterruptedException since this scrapes data from baskeball-reference.com, we may have to
     *                              pauseExecutionIfNecessary()
     */
    // getStats could be edited to guarantee no more than one connection per game
    // currently, it connects once per birthday boy for a given game
    // however, over the course of the season, there are so few multiple-birthday-boy games that this savings would be minimal
    public static String getStats(String player, LocalDate gameDate, String homeTeam) throws IOException, InterruptedException {
        String statLine = "";

        pauseExecutionIfNecessary();

        URL boxScorePage = new URL("https://www.basketball-reference.com/boxscores/" +
                gameDate.toString().replace("-", "") + "0" +
                homeTeam + ".html");
        HttpURLConnection boxScoreConnection = (HttpURLConnection) boxScorePage.openConnection();
        BRUrlConnectionsThisHour++;
        InputStream boxScoreInputStream = boxScoreConnection.getInputStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(boxScoreInputStream))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.contains(player)) {
                    if (line.contains("Did Not Play")) {
                        statLine += " (DNP)";
                        break;
                    }
                    else if (line.contains("Did Not Dress")) {
                        statLine += " (DND)";
                        break;
                    }
                    else {
                        statLine += " (";
                        int index = line.indexOf("data-stat=\"mp\"");
                        while (!line.substring(index, index + 1).equals(">")) {
                            index++;
                        }
                        index++;
                        while (!line.substring(index, index + 1).equals("<")) {
                            statLine += line.substring(index, index + 1);
                            index++;
                        }
                        statLine += " mp, ";

                        index = line.indexOf("data-stat=\"pts\"");
                        while (!line.substring(index, index + 1).equals(">")) {
                            index++;
                        }
                        index++;
                        while (!line.substring(index, index + 1).equals("<")) {
                            statLine += line.substring(index, index + 1);
                            index++;
                        }
                        statLine += " pts, ";

                        index = line.indexOf("data-stat=\"fg\"");
                        while (!line.substring(index, index + 1).equals(">")) {
                            index++;
                        }
                        index++;
                        while (!line.substring(index, index + 1).equals("<")) {
                            statLine += line.substring(index, index + 1);
                            index++;
                        }
                        statLine += "/";

                        index = line.indexOf("data-stat=\"fga\"");
                        while (!line.substring(index, index + 1).equals(">")) {
                            index++;
                        }
                        index++;
                        while (!line.substring(index, index + 1).equals("<")) {
                            statLine += line.substring(index, index + 1);
                            index++;
                        }
                        statLine += " fga, ";

                        index = line.indexOf("data-stat=\"trb\"");
                        while (!line.substring(index, index + 1).equals(">")) {
                            index++;
                        }
                        index++;
                        while (!line.substring(index, index + 1).equals("<")) {
                            statLine += line.substring(index, index + 1);
                            index++;
                        }
                        statLine += " reb, ";

                        index = line.indexOf("data-stat=\"ast\"");
                        while (!line.substring(index, index + 1).equals(">")) {
                            index++;
                        }
                        index++;
                        while (!line.substring(index, index + 1).equals("<")) {
                            statLine += line.substring(index, index + 1);
                            index++;
                        }
                        statLine += " ast)";
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            return "";
        }
        boxScoreConnection.disconnect();
        return statLine;
    }

    /**
     * gets all birthday boys (and their stats, if applicable) for teamAbbreviation's game on gameDate
     * @param teamAbbreviation      e.g., "ATL"
     * @param gameDate              a LocalDate representation of the game date
     * @param homeTeamAbbreviation  e.g., "ATL". Note that teamAbbreviation and homeTeamAbbreviation may be equal.
     * @return a String containing all birthday boys (and their stats, if applicable)
     * @throws IOException
     * @throws InterruptedException since this scrapes data from baskeball-reference.com, we may have to
     *                              pauseExecutionIfNecessary()
     */
    public static String getBirthdays(String teamAbbreviation, LocalDate gameDate, String homeTeamAbbreviation) throws IOException, InterruptedException {
        LocalDate localDayBeforeGame = gameDate.minusDays(1);
        MonthDay localMonthDayBeforeGame = java.time.MonthDay.of(localDayBeforeGame.getMonthValue(), localDayBeforeGame.getDayOfMonth());

        String out = "";

        if (inSeasonTeamBirthdays.get(teamAbbreviation).get(localMonthDayBeforeGame) != null) {
            //System.out.print(teamAbbreviation + ": ");
            out += teamAbbreviation + ": ";
            for (int i = 0; i < inSeasonTeamBirthdays.get(teamAbbreviation).get(localMonthDayBeforeGame).size(); i++) {
                int age = localDayBeforeGame.getYear() - Integer.parseInt(inSeasonTeamBirthdays.get(teamAbbreviation).get(localMonthDayBeforeGame).get(i)[1]);
                String turn = " turns ";
                String stats = "";
                if (java.time.LocalDate.now().isAfter(gameDate)) {
                    turn = " turned ";
                    stats = getStats(inSeasonTeamBirthdays.get(teamAbbreviation).get(localMonthDayBeforeGame).get(i)[0], gameDate, homeTeamAbbreviation);
                }
                //System.out.print(inSeasonTeamBirthdays.get(teamAbbreviation).get(localMonthDayBeforeGame).get(i)[0] + turn + age + stats);
                out += inSeasonTeamBirthdays.get(teamAbbreviation).get(localMonthDayBeforeGame).get(i)[0] +
                        turn + age + stats;
                if (i != inSeasonTeamBirthdays.get(teamAbbreviation).get(localMonthDayBeforeGame).size() - 1) {
                    //System.out.print(", ");
                    out += ", ";
                }
            }
            //System.out.println("");
            out += System.lineSeparator();
        }

        return out;
    }

    /**
     * creates a .txt file for monthNumber containing all birthday games
     * @param monthNumber   e.g., 10 for "October"
     * @throws IOException
     * @throws InterruptedException
     */
    public static void setBirthdayGamesInMonthFile(int monthNumber) throws IOException, InterruptedException {
        String path = "Season" + season + "/" + MONTHS.get(monthNumber) + ".txt";
        try {
            File file = new File(path);

            // check whether the file already exists
            // if not, create it and write all the birthdays to it
            if (!file.exists()) {
                file.createNewFile();
                writeBirthdayGamesToMonthFile(monthNumber, file);
                System.out.println("Created " + path);
            }
            // if the .txt file already exists...
            else {
                int year = season;
                if (monthNumber == 10 || monthNumber == 11) {
                    year--;
                }
                // check the last time the .txt file was modified
                // if it was before the end of the month, overwrite its contents
                if (java.time.LocalDate.ofEpochDay(file.lastModified()/Long.valueOf(1000*60*60*24))
                        .isBefore(java.time.LocalDate.of(year, monthNumber % 12 + 1, 1))) {
                    writeBirthdayGamesToMonthFile(monthNumber, file);
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

    /**
     * writes content to the monthNumber birthday games file
     * @param monthNumber   e.g., 10 for "October"
     * @param file          e.g. October.txt
     * @throws IOException
     * @throws InterruptedException since this scrapes data from baskeball-reference.com, we may have to
     *                              pauseExecutionIfNecessary()
     */
    public static void writeBirthdayGamesToMonthFile(int monthNumber, File file) throws IOException, InterruptedException {
        pauseExecutionIfNecessary();

        // Make a URL to the web page
        URL schedulePage = new URL("https://www.basketball-reference.com/leagues/NBA_" +
                season + "_games-" +
                MONTHS.get(monthNumber).toLowerCase() +
                ".html#schedule");

        // Get the input stream through URL Connection
        HttpURLConnection scheduleConnection = (HttpURLConnection) schedulePage.openConnection();
        BRUrlConnectionsThisHour++;
        InputStream scheduleInputStream = scheduleConnection.getInputStream();

        String fileContents = "";

        try (BufferedReader br = new BufferedReader(new InputStreamReader(scheduleInputStream))) {
            String line = null;
            boolean colgroup = false;
            int tbodyCount = 0;
            boolean endTable = false;
            boolean inTable = false;
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
                    while (!line.substring(dayEndIndex, dayEndIndex + 1).equals("&")) {
                        dayEndIndex++;
                    }
                    int day = Integer.parseInt(line.substring(dayStartIndex, dayEndIndex));

                    // set gameDate
                    int year = season;
                    if (monthNumber > 6) {
                        year--;
                    }
                    LocalDate gameDate = java.time.LocalDate.of(year, monthNumber, day);

                    // get teams who played in that game: visitor and home
                    int teamIndexStart = line.indexOf("visitor_team_name");
                    while (!Character.isUpperCase(line.substring(teamIndexStart, teamIndexStart + 1).charAt(0))) {
                        teamIndexStart++;
                    }
                    String visitor = line.substring(teamIndexStart, teamIndexStart + 3);
                    teamIndexStart += 3;
                    while (!Character.isUpperCase(line.substring(teamIndexStart, teamIndexStart + 1).charAt(0))) {
                        teamIndexStart++;
                    }
                    String home = line.substring(teamIndexStart, teamIndexStart + 3);

                    // birthdate search will be on day before gameDate
                    LocalDate dayBeforeGame = gameDate.minusDays(1);
                    MonthDay monthDayBeforeGame = java.time.MonthDay.of(dayBeforeGame.getMonthValue(), dayBeforeGame.getDayOfMonth());

                    if (inSeasonTeamBirthdays.get(visitor).get(monthDayBeforeGame) != null ||
                            inSeasonTeamBirthdays.get(home).get(monthDayBeforeGame) != null) {
                        fileContents += System.lineSeparator() + day + ": " + visitor + " at " + home;

                        // check whether the game is in the past
                        if (java.time.LocalDate.now().isAfter(gameDate)) {
                            String score = "";
                            // if it is, get the game score
                            int scoreIndexStart = line.indexOf("visitor_pts");
                            while (!line.substring(scoreIndexStart, scoreIndexStart + 1).equals(">")) {
                                scoreIndexStart++;
                            }
                            scoreIndexStart++;
                            int scoreIndexStop = scoreIndexStart;
                            while (!line.substring(scoreIndexStop, scoreIndexStop + 1).equals("<")) {
                                scoreIndexStop++;
                            }
                            score += line.substring(scoreIndexStart, scoreIndexStop) + "-";

                            scoreIndexStart = line.indexOf("home_pts");
                            while (!line.substring(scoreIndexStart, scoreIndexStart + 1).equals(">")) {
                                scoreIndexStart++;
                            }
                            scoreIndexStart++;
                            scoreIndexStop = scoreIndexStart;
                            while (!line.substring(scoreIndexStop, scoreIndexStop + 1).equals("<")) {
                                scoreIndexStop++;
                            }
                            score += line.substring(scoreIndexStart, scoreIndexStop);
                            // System.out.print(", " + score);
                            fileContents += ", " + score;
                        }
                        fileContents += System.lineSeparator();

                        // print all visitor team birthdays
                        fileContents += getBirthdays(visitor, gameDate, home);
                        // print all home team birthdays
                        fileContents += getBirthdays(home, gameDate, home);
                    }
                }
            }
        }
        scheduleConnection.disconnect();

        FileWriter fw = new FileWriter(file);
        PrintWriter pw = new PrintWriter(fw);
        // clear any old contents of the file before writing updated fileContents
        pw.flush();
        pw.write(fileContents);
        pw.close();
        fw.close();
    }

    /**
     * basketball-reference.com seems to be throwing a 429 error if you exceed 30 requests per hour
     * pauses execution when necessary, and resumes after sufficient time has elapsed
     * @throws InterruptedException
     */
    public static void pauseExecutionIfNecessary() throws InterruptedException {
        if (BRUrlConnectionsThisHour >= 30) {
            int waitTime = 61;
            System.out.println("Execution paused for " + waitTime +
                    " minutes to avoid 429 errors from basketball-reference.com.");
            System.out.println("Execution will resume at " +
                    java.time.LocalTime.now().plus(Duration.ofMinutes(waitTime)).format(DateTimeFormatter.ofPattern("hh:mm a")) + ".");
            while (waitTime > 10) {
                TimeUnit.MINUTES.sleep(10);
                waitTime -= 10;
                System.out.println(waitTime + " minutes until execution resumes.");
            }
            TimeUnit.MINUTES.sleep(waitTime);
            System.out.println("Execution resumed.");
            BRUrlConnectionsThisHour = 0;
        }
    }

    /**
     * creates Statistics.txt, which contains a win-loss record for day-after-birthday-games
     * and a field goal percentage for birthday boys in those games
     * @param todaysDate    a LocalDate representation of today's date
     * @throws IOException
     * @throws InterruptedException
     */
    // future idea: separate out stats for sub-25th birthdays
    public static void makeStatisticsFile(LocalDate todaysDate) throws IOException, InterruptedException {
        int wins = 0;
        int losses = 0;
        int fgm = 0;
        int fga = 0;
        LinkedList<Integer> monthNumbersWithCompleteData = new LinkedList<>();
        LinkedList<Integer> monthNumbersWithIncompleteData = new LinkedList<>();
        for (int monthNumber : MONTHS.keySet()) {
            int year = season;
            if (monthNumber >= 10) {
                year--;
            }
            // if the month in question has already begun, then we are interested in data from that month
            if (java.time.LocalDate.now().isAfter(java.time.LocalDate.of(year, monthNumber, 1))) {
                String path = "Season" + season + "/" + MONTHS.get(monthNumber) + ".txt";
                File file = new File(path);

                // check whether the month file already exists
                if (file.exists()) {
                    // check the date the .txt file was last modified
                    // and mark the data as complete/incomplete accordingly
                    if (monthNumber == 12) {
                        year++;
                    }
                    if (java.time.LocalDate.ofEpochDay(file.lastModified() / Long.valueOf(1000 * 60 * 60 * 24))
                            .isAfter(java.time.LocalDate.of(year, monthNumber % 12 + 1, 1))) {
                        monthNumbersWithCompleteData.add(monthNumber);
                    }
                    else {
                        monthNumbersWithIncompleteData.add(monthNumber);
                    }
                    // read through existing/relevant month files and tally stats
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line = null;
                        // read each line
                        while ((line = br.readLine()) != null) {
                            // checks that the game has already occurred
                            if (line.contains(" at ") && line.contains("-")) {
                                String[] lineArray = line.split(" ");
                                String away = lineArray[1];
                                String home = lineArray[3].substring(0,3);
                                boolean awayTeamWon = Integer.parseInt(lineArray[4].split("-")[0]) > Integer.parseInt(lineArray[4].split("-")[1]);
                                // following two lines may contain birthday boys (as birthday-boys for different
                                // teams are printed on separate lines)
                                String oneLineLater = br.readLine();
                                String twoLinesLater = br.readLine();

                                // the away team has a birthday boy
                                if (oneLineLater.contains(away) || (twoLinesLater != null && twoLinesLater.contains(away))) {
                                    // and the away team won
                                    if (awayTeamWon) {
                                        wins++;
                                    }
                                    else {
                                        losses++;
                                    }
                                }
                                // the home team has a birthday boy
                                if (oneLineLater.contains(home) || (twoLinesLater != null && twoLinesLater.contains(home))) {
                                    // but the home team lost
                                    if (awayTeamWon) {
                                        losses++;
                                    }
                                    else {
                                        wins++;
                                    }
                                }
                            }
                        }
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line = null;
                        // read each line
                        while ((line = br.readLine()) != null) {
                            // checks that the line contains player stats for birthday-boys
                            if (line.contains(" turned ") && line.contains(" fga, ")) {
                                // while loop allows for multiple birthdays on the same team
                                while (line.indexOf("(") != -1) {
                                    line = line.substring(line.indexOf("(")+1);
                                    String statLine = line.substring(0, line.indexOf(")"));
                                    String[] fgmFga = statLine.split(", ")[2].split(" ")[0].split("/");
                                    // increments field goals made/attempted by birthday boys
                                    fgm += Integer.parseInt(fgmFga[0]);
                                    fga += Integer.parseInt(fgmFga[1]);
                                }
                            }
                        }
                    }
                    catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        String path = "Season" + season + "/" + "Statistics.txt";
        try {
            File file = new File(path);

            // check whether the file already exists
            // if not, create it
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter fw = new FileWriter(file);
            PrintWriter pw = new PrintWriter(fw);
            // clear any old contents of the file before writing updated file contents
            pw.flush();
            pw.write("As of " + todaysDate + ", and using the data you've collected from games in:" +
                    System.lineSeparator());
            for (int monthNumber : monthNumbersWithCompleteData) {
                pw.write(" - " + MONTHS.get(monthNumber) + System.lineSeparator());
            }
            for (int monthNumber : monthNumbersWithIncompleteData) {
                pw.write(" - part of " + MONTHS.get(monthNumber) + System.lineSeparator());
            }
            pw.write("Birthday-teams' record in birthday-games: " + wins + "-" + losses + System.lineSeparator());
            pw.write("FG% for birthday-boys in birthday-games: " + String.format("%.3f", (double) fgm / fga) +
                    " (league avg: " + getLeagueAvgFGPct() + ")" + System.lineSeparator());
            pw.close();
            fw.close();

            System.out.println("You now see birthday-game statistics at " + path);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * retrieves the current league average field goal percentage from basketball-reference.com
     * which will be printed in Statistics.txt, so users can compare it to that of birthday boys in birthday games
     * @return the field goal percentage, as a String, in the form "0.xxx"
     * @throws IOException
     * @throws InterruptedException
     */
    public static String getLeagueAvgFGPct() throws IOException, InterruptedException {
        // so that percentage is formatted "0.xxx" rather than ".xxx"
        String fg = "0";

        pauseExecutionIfNecessary();

        URL boxScorePage = new URL("https://www.basketball-reference.com/leagues/NBA_" +
                season + ".html");
        HttpURLConnection boxScoreConnection = (HttpURLConnection) boxScorePage.openConnection();
        BRUrlConnectionsThisHour++;
        InputStream boxScoreInputStream = boxScoreConnection.getInputStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(boxScoreInputStream))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.contains("id=\"shooting-team\"") && line.contains("data-stat=\"fg_pct\" >")){
                    int index = line.indexOf("League Average");
                    line = line.substring(index);
                    index = line.indexOf("data-stat=\"fg_pct\" >") + "data-stat=\"fg_pct\" >".length();
                    fg += line.substring(index, index+4);
                }
            }
        }
        catch (Exception e) {
            return "";
        }
        boxScoreConnection.disconnect();
        return fg;
    }
}