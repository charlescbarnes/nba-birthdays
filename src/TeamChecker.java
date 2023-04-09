import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Scanner;

public class TeamChecker {
    private final int SEASON;
    private HashMap<String, LinkedList<Integer>> allTeamRosters;
    private boolean newWebScrapeNeeded;


    /**
     * Class constructor
     * @param season the NBA season, as an <code>int</code> (e.g., 2023)
     */
    public TeamChecker(int season) {
        this.SEASON = season;
        allTeamRosters = new HashMap<>();
        for (String team : Nba.TEAMS.keySet()) {
            allTeamRosters.put(team, new LinkedList<>());
        }
    }


    /**
     * setter method for <code>HashMap<String, LinkedList<Integer>> allTeamRosters</code>,
     * which maps each NBA team (i.e., their <code>String</code> abbreviation; e.g., "ATL")
     * to a list of months of the NBA season in which their rosters were locally saved
     */
    public void setAllTeamRosters() {
        // check to see if there's a folder for this season
        File allTeamRostersFolder = new File("Season" + SEASON + "/TeamRosters");
        // if there isn't, make one
        if (!allTeamRostersFolder.exists()) {
            allTeamRostersFolder.mkdir();
            System.out.println("Since you didn't already have one, I created a Season" +
                    SEASON + "/TeamRosters directory for you.");
            // if there wasn't already a folder, we know data for all teams (and all months) is missing
            newWebScrapeNeeded = true;
        }
        else {
            // otherwise, for each team...
            for (String team : Nba.TEAMS.keySet()) {
                // and every month in the season
                for (Integer monthNumber : Nba.MONTHS.keySet()) {
                    String path = allTeamRostersFolder + "/" + team + monthNumber + ".txt";
                    try {
                        File file = new File(path);
                        // check whether the roster file already exists
                        if (file.exists()) {
                            allTeamRosters.get(team).add(monthNumber);
                        }
                    } 
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }


    /**
     * setter method for <code>boolean newWebScrapeNeeded</code>, which determines whether we
     * will make new HTTP requests to basketball-reference.com for each team, saving updated
     * roster files locally
     */
    public void setNewWebScrapeNeeded() {
        // if newWebScrapeNeeded has already been set to true, we want it to remain so
        // only act if newWebScrapeNeeded is currently false
        int lastUpdateMonth = 13;
        if (!newWebScrapeNeeded) {
            for (LinkedList<Integer> list: allTeamRosters.values()) {
                // if any team has no rosters saved, we need to newWebScrapeNeeded
                if (list.isEmpty()) {
                    newWebScrapeNeeded = true;
                    break;
                }
                else {
                    if (list.getLast() < lastUpdateMonth) {
                        lastUpdateMonth = list.getLast();
                    }
                }
            }
        }
        if (!newWebScrapeNeeded) {
            int year = SEASON;
            if (lastUpdateMonth > 8) {
                year--;
            }
            System.out.println("Your team roster data was last retrieved in " +
                    Nba.MONTHS.get(lastUpdateMonth) + " of " + year + ".");
            System.out.println("Do you want me to retrieve updated data from basketball-reference.com?");
            System.out.println("(Note: Doing so will likely add an hour to program execution.)");
            System.out.print("Enter \"Y\" or \"N\": ");

            Scanner scan = new Scanner(System.in);
            String answer = scan.next().trim();

            while (!answer.equals("Y") && !answer.equals("N")) {
                System.out.print("Please enter \"Y\" or \"N\": ");
                answer = scan.next();
            }
            newWebScrapeNeeded = answer.equals("Y");
        }
    }

    public boolean isNewWebScrapeNeeded() { return newWebScrapeNeeded; }
}