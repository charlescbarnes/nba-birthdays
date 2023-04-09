import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Scanner;

public class Statistics {
    private final int SEASON;


    /**
     * Class constructor
     * @param season the NBA season, as an <code>int</code> (e.g., 2023)
     */
    public Statistics(int season) {
        this.SEASON = season;
    }


    /**
     * retrieves the current league average field goal percentage from basketball-reference.com
     * which will be printed in Statistics.txt, so users can compare it to that of birthday boys in birthday games
     * @return the field goal percentage, as a String, in the form "0.xxx"
     * @throws IOException
     * @throws InterruptedException since this scrapes data from basketball-reference.com, we may have to
     *                              <code>Time.pauseExecutionIfNecessary()</code>
     */
    public String scrapeLeagueAvgFGPct() throws IOException, InterruptedException {
        // so that percentage is formatted "0.xxx" rather than ".xxx"
        String fg = "0";

        Time.pauseExecutionIfNecessary();

        URL boxScorePage = new URL("https://www.basketball-reference.com/leagues/NBA_" +
                SEASON + ".html");
        HttpURLConnection boxScoreConnection = (HttpURLConnection) boxScorePage.openConnection();
        Time.BRUrlConnectionsThisHour++;
        InputStream boxScoreInputStream = boxScoreConnection.getInputStream();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(boxScoreInputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("id=\"shooting-team\"") && line.contains("data-stat=\"fg_pct\" >")) {
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


    /**
     * creates Statistics.txt, which contains a win-loss record for day-after-birthday-games
     * and a field goal percentage for birthday boys in those games
     * @throws InterruptedException
     */
    // future idea: separate out stats for sub-25th birthdays
    public void makeStatisticsFile() throws InterruptedException {
        int wins = 0;
        int losses = 0;
        int fgm = 0;
        int fga = 0;
        LinkedList<Integer> monthNumbersWithCompleteData = new LinkedList<>();
        LinkedList<Integer> monthNumbersWithIncompleteData = new LinkedList<>();
        for (int monthNumber : Nba.MONTHS.keySet()) {
            int year = SEASON;
            if (monthNumber >= 10) {
                year--;
            }
            // if the month in question has already begun, then we are interested in data from that month
            if (java.time.LocalDate.now().isAfter(java.time.LocalDate.of(year, monthNumber, 1))) {
                String path = "Season" + SEASON + "/" + Nba.MONTHS.get(monthNumber) + ".txt";
                File file = new File(path);

                // check whether the month file already exists
                if (file.exists()) {
                    // check the date the .txt file was last modified
                    // and mark the data as complete/incomplete accordingly
                    if (monthNumber == 12) {
                        year++;
                    }
                    if (java.time.LocalDate.ofEpochDay(file.lastModified() / (long) (1000 * 60 * 60 * 24))
                            .isAfter(java.time.LocalDate.of(year, monthNumber % 12 + 1, 1))) {
                        monthNumbersWithCompleteData.add(monthNumber);
                    }
                    else {
                        monthNumbersWithIncompleteData.add(monthNumber);
                    }
                    // read through existing/relevant month files and tally stats
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line;
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
                        String line;
                        // read each line
                        while ((line = br.readLine()) != null) {
                            // checks that the line contains player stats for birthday-boys
                            if (line.contains(" turned ") && line.contains(" fga, ")) {
                                // while loop allows for multiple birthdays on the same team
                                while (line.contains("(")) {
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

        String path = "Season" + SEASON + "/" + "Statistics.txt";
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
            pw.write("As of " + Time.today + ", and using the data you've collected from games in:" +
                    System.lineSeparator());
            for (int monthNumber : monthNumbersWithCompleteData) {
                pw.write(" - " + Nba.MONTHS.get(monthNumber) + System.lineSeparator());
            }
            for (int monthNumber : monthNumbersWithIncompleteData) {
                pw.write(" - part of " + Nba.MONTHS.get(monthNumber) + System.lineSeparator());
            }
            pw.write("Birthday-teams' record in birthday-games: " + wins + "-" + losses + System.lineSeparator());
            pw.write("FG% for birthday-boys in birthday-games: " + String.format("%.3f", (double) fgm / fga) +
                    " (league avg: " + scrapeLeagueAvgFGPct() + ")" + System.lineSeparator());
            pw.close();
            fw.close();

            System.out.println("You now see birthday-game statistics at " + path);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * asks the user whether they'd like a new Statistics.txt file saved locally
     * (and does it if the answer is yes)
     * @throws InterruptedException since this scrapes data from basketball-reference.com, we may have to
     *                              <code>Time.pauseExecutionIfNecessary()</code>
     */
    public void run() throws InterruptedException {
        System.out.println("Do you want me to gather birthday-game stats?");
        System.out.print("Enter \"Y\" or \"N\": ");

        Scanner scan = new Scanner(System.in);
        String answer = scan.next().trim();

        while (!answer.equals("Y") && !answer.equals("N")) {
            System.out.print("Please enter \"Y\" or \"N\": ");
            answer = scan.next();
        }
        if (answer.equals("Y")) {
            makeStatisticsFile();
        }

        System.out.println("This is all I have to offer for now. Enjoy!");
    }
}