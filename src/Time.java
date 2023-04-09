import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class Time {
    public static LocalDate today = java.time.LocalDate.now();


    /**
     * static counter variable to track how many requests have been made to basketball-reference.com
     */
    public static int BRUrlConnectionsThisHour = 0;


    /**
     * @throws InterruptedException basketball-reference.com seems to be throwing a 429 error if
     *                              you exceed 30 requests per hour
     *                              This method pauses execution when necessary, and resumes after
     *                              sufficient time has elapsed.
     */
    public static void pauseExecutionIfNecessary() throws InterruptedException {
        if (BRUrlConnectionsThisHour >= 30) {
            int waitTime = 61;
            System.out.println("Execution paused for " + waitTime +
                    " minutes to avoid 429 errors from basketball-reference.com.");
            System.out.println("Execution will resume at " +
                    java.time.LocalTime.now()
                            .plus(Duration.ofMinutes(waitTime))
                            .format(DateTimeFormatter.ofPattern("hh:mm a")) + ".");
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
}