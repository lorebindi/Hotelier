import java.util.Scanner;

/* Questa classe è utilizzata unicamente per evitare accavallamenti tra le scritture
 e le letture su e da console da parte del ClientMain e del ListeningUDPTask. */
public class ConsoleManage {
    public static synchronized void synchronizedPrint(String message){
        System.out.print(message);
    }

    public static synchronized void synchronizedErrPrint(String message){
        System.err.print(message);
    }

    public static synchronized int synchronizedIntegerRead(String prompt, Scanner scanner){
        ConsoleManage.synchronizedPrint(prompt);
        int integer = 0;
        boolean isValidInput = false; // Flag per uscire dal ciclo.
        while (!isValidInput) {
            String temp = scanner.nextLine();
            try {
                integer = Integer.parseInt(temp);
                isValidInput = true;
            } catch (NumberFormatException e) {
                ConsoleManage.synchronizedErrPrint("Invalid format. Please enter a number.\n");
            }
        }
        return integer;
    }

    public static synchronized String synchronizedStringRead(String prompt, Scanner scanner){
        ConsoleManage.synchronizedPrint("Enter "+ prompt + ": ");
        String inputString = scanner.nextLine();
        // Controlli sulla stringa.
        while(inputString.isEmpty()) {
            ConsoleManage.synchronizedPrint(prompt + " field cannot be left blank. \nRe-enter "+ prompt + ": ");
            inputString = scanner.nextLine();
        }
        return inputString;
    }
}
