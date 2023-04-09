import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class MonthChecker {
    private final int SEASON;
    private ArrayList<Integer> missingMonths;
    private ArrayList<Integer> partialMonths;
    private ArrayList<Integer> completedMonths;
    private ArrayList<Integer> monthsToFetch;


    /**
     * Class constructor
     * @param season the NBA season, as an <code>int</code> (e.g., 2023)
     */
    public MonthChecker(int season) {
        this.SEASON = season;
        missingMonths = new ArrayList<>();
        partialMonths = new ArrayList<>();
        completedMonths = new ArrayList<>();
    }


    /**
     * setter method for <code>ArrayList<Integer> missingMonths</code>, which tracks which months of the NBA season
     * do not have locally saved birthday-game files
     * also creates a directory for the current <code>SEASON</code> if one does not already exist
     */
    public void setMissingMonths() {
        // check to see if there's a folder for this season
        File seasonFolder = new File("Season" + SEASON);
        // if there isn't, make one
        if (!seasonFolder.exists()) {
            seasonFolder.mkdir();
            System.out.println("Since you didn't already have one, I created a Season" +
                    SEASON + " directory for you.");
            // if there wasn't already a folder, we know data for all MONTHS is missing
            missingMonths = new ArrayList<>(Nba.MONTHS.keySet());
        }
        else {
            // otherwise, for each month...
            for (Integer monthNumber : Nba.MONTHS.keySet()) {
                String path = "Season" + SEASON + "/" + Nba.MONTHS.get(monthNumber) + ".txt";
                try {
                    File file = new File(path);

                    // check whether the birthday-game file already exists
                    // if the birthday-game file doesn't exist, add it to the list
                    if (!file.exists()) {
                        missingMonths.add(monthNumber);
                    }
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    /**
     * setter method for <code>ArrayList<Integer> partialMonths</code>, which tracks which months of the NBA season
     * have had locally saved birthday-game files created and saved but not updated with all results/statistics
     */
    public void setPartialMonths() {
        for (Integer monthNumber : Nba.MONTHS.keySet()) {
            String path = "Season" + SEASON + "/" + Nba.MONTHS.get(monthNumber) + ".txt";
            try {
                File file = new File(path);

                // check whether the birthday-game file already exists
                if (file.exists()) {
                    int year = SEASON;
                    // October and November are in-season months such that
                    // the start of the next month is still one less than the "season"
                    // e.g., the start of the month after 10/2023 is 11/2023,
                    // but the start of the month after 12/2023 is 1/2024.
                    if (monthNumber == 10 || monthNumber == 11) {
                        year--;
                    }
                    // check the date the .txt file was last modified
                    // if it was before the end of the month, then that month is missing data--add it to the list
                    if (java.time.LocalDate.ofEpochDay(file.lastModified() / (long) (1000 * 60 * 60 * 24))
                            .isBefore(java.time.LocalDate.of(year, monthNumber % 12 + 1, 1))) {
                        partialMonths.add(monthNumber);
                    }
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * setter method for <code>ArrayList<Integer> completedMonths</code>, which tracks which months of the NBA season
     * have had locally saved birthday-game files created and saved and are finalized
     * (all available results/statistics are saved to the file)
     */
    public void setCompletedMonths() {
        completedMonths = new ArrayList<>(Nba.MONTHS.keySet());
        completedMonths.removeAll(missingMonths);
        completedMonths.removeAll(partialMonths);
    }

    public void printCompletedMonths() {
        // if user already has some complete month files saved, let them know
        if (!completedMonths.isEmpty()) {
            System.out.println("You appear to have complete birthday-game data for:");
            for (Integer monthNumber : completedMonths) {
                System.out.println(" - " + Nba.MONTHS.get(monthNumber));
            }
        }
    }


    /**
     * setter method for <code>ArrayList<Integer> monthsToFetch</code>, which tracks which months of the NBA season
     * for which the user wants to retrieve new data
     */
    public void setMonthsToFetch() {
        monthsToFetch = new ArrayList<>();
        if (!partialMonths.isEmpty() || !missingMonths.isEmpty()) {
            System.out.println("Which months' birthday-games do you want me to retrieve?");
            System.out.println(" - For all missing months, enter \"0\".");
            System.out.print(" - For just one month, enter the month number (");
            // list all monthNumber-month pairs for months with missing data
            ArrayList<Integer> incompleteMonths = new ArrayList<>(partialMonths);
            incompleteMonths.addAll(missingMonths);
            for (int i = 0; i < incompleteMonths.size(); i++) {
                System.out.print("\"" + incompleteMonths.get(i) + "\" for " +
                        Nba.MONTHS.get(incompleteMonths.get(i)));
                if (i != incompleteMonths.size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println(").");
            System.out.println(" - To not run any new months, enter \"100\".");
            System.out.print("Month number: ");

            String monthSelectionString;
            boolean monthSelectionValid = false;
            int monthSelection = -1;
            Scanner scan = new Scanner(System.in);
            while (!monthSelectionValid) {
                monthSelectionString = scan.next().trim();
                // check whether the user entered a valid option. If not, repeat prompt.
                try {
                    monthSelection = Integer.parseInt(monthSelectionString);
                    if (monthSelection == 0 || incompleteMonths.contains(monthSelection) ||
                            monthSelection == 100) {
                        monthSelectionValid = true;
                    } else {
                        System.out.print("Please enter a valid (see above) month number: ");
                    }
                } catch (NumberFormatException e) {
                    System.out.print("Please enter a valid (see above) month number: ");
                }
            }
            // add selected months to monthsToFetch
            if (monthSelection == 0) {
                monthsToFetch = incompleteMonths;
                System.out.println("Got it. I'll get birthday-games for the rest of the season for you.");
            } else if (incompleteMonths.contains(monthSelection)) {
                monthsToFetch.add(monthSelection);
                System.out.println(Nba.MONTHS.get(monthSelection) + " birthday-games, coming right up!");
            }
        }
        else {
            System.out.println("You appear to already have complete birthday-game data for the whole " + SEASON +
                    " season.");
        }
    }

    public ArrayList<Integer> getMonthsToFetch() { return monthsToFetch; }
}