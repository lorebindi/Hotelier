import java.nio.channels.Selector;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class StopServerTask implements Runnable{
    private static AtomicBoolean stop = new AtomicBoolean(false);
    private static Scanner scanner;
    private static Selector selector;

    public StopServerTask(AtomicBoolean stop, Scanner scanner, Selector selector) {
        StopServerTask.stop = stop;
        StopServerTask.scanner = scanner;
        StopServerTask.selector = selector;
    }

    public void run() {
        System.out.println("Enter any character to end safely the server.");
        StopServerTask.scanner.nextLine();
        stop.set(true);
        selector.wakeup(); // Sveglia il selector
    }
}
