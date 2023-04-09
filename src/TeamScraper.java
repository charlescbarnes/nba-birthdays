import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class TeamScraper {
    private final int SEASON;


    /**
     * Class constructor
     * @param season the NBA season, as an <code>int</code> (e.g., 2023)
     */
    public TeamScraper(int season) {
        this.SEASON = season;
    }


    /**
     * scrapes team roster data
     * @param team  a <code>String</code> representation of the team abbreviation, e.g., "ATL"
     * @return      the HTML code from basketball-reverence.com that produces the table containing
     *              the latest <code>team</code> roster
     */
    public String getTeamRosterHTML(String team) {
        StringBuilder pageContents = new StringBuilder();
        try {
            Time.pauseExecutionIfNecessary();

            URL teamPage = new URL("https://www.basketball-reference.com/teams/" + team +
                    "/" + SEASON + ".html#roster");

            // using HttpURLConnection so that I can .disconnect() when done
            HttpURLConnection teamConnection = (HttpURLConnection) teamPage.openConnection();
            Time.BRUrlConnectionsThisHour++;
            InputStream teamInputStream = teamConnection.getInputStream();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(teamInputStream))) {
                String line;
                // read each line
                while ((line = br.readLine()) != null) {
                    // checks if line is part of Roster table (contains birthday)
                    if (line.contains("<tr") && line.contains("birth_date")) {
                        pageContents.append(line).append(System.lineSeparator());
                    }
                }
            }
            teamInputStream.close();
            teamConnection.disconnect();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return pageContents.toString();
    }


    /**
     * writes the Roster table HTML (containing birthdays) from basketball-reference.com to local .txt files
     */
    public void makeNewTeamRosterFiles() {
        System.out.println("Gathering roster data from BasketballReference.com...");

        int month = Time.today.getMonthValue();
        ArrayList<Integer> inSeasonMonths = new ArrayList<>(Nba.MONTHS.keySet());
        int firstInSeasonMonth = inSeasonMonths.get(0);
        int lastInSeasonMonth = inSeasonMonths.get(inSeasonMonths.size() - 1);
        // if gathering data from a previous season, ...
        if (Nba.getCurrentSeason() > SEASON) {
            // make the month the last in-season month
            month = lastInSeasonMonth;
        }
        // otherwise, we're gathering data from this season,
        // but if it's prior to the start of the season...
        else if (month < firstInSeasonMonth && month > lastInSeasonMonth) {
            // make the month the first in-season month
            month = firstInSeasonMonth;
        }
        int finalMonth = month;

        Nba.TEAMS.keySet().parallelStream().forEach( (team) -> {

            String path = "Season" + SEASON + "/TeamRosters/" + team + finalMonth + ".txt";
            try {
                File file = new File(path);

                if (!file.exists()) {
                    file.createNewFile();
                }

                FileWriter fw = new FileWriter(path);
                PrintWriter pw = new PrintWriter(fw);
                // clear any old contents of the file before writing updated pageContents
                pw.flush();
                pw.write(getTeamRosterHTML(team));
                pw.close();
                fw.close();
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        });
        System.out.println("Okay, rosters are set.");
    }
}