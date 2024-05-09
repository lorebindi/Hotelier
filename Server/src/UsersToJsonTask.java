import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Questa è la classe che rappresenta il task lato Server che serializza
 * gli oggetti User nel file Users.json.
 */
public class UsersToJsonTask implements Runnable{
    
    private final String FILE_CLIENTS_PATH = "Files/Json/Users.json";
    
    /* Scrive tutti gli utenti sul file endHotel.json*/
    public void run() {
        List<User> userList = new ArrayList<> (ServerMain.getUsers().values());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(this.FILE_CLIENTS_PATH).toAbsolutePath().normalize().toString()))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            // Serializzazione della lista in formato JSON.
            String jsonString = gson.toJson(userList);
            // Scrittura del JSON nel file.
            writer.write(jsonString);
        } catch (IOException ex) {
            System.err.println("I/O error from UsersToJsonTask function.");
        }
    }
}
