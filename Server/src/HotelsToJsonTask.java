import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Questa è la classe che rappresenta il task lato Server che serializza
 * gli oggetti Hotel nel file endHotels.json.
 */
public class HotelsToJsonTask implements Runnable{
    private final String END_FILE_HOTELS_PATH = "Files/Json/endHotels.json";

    /**
     * Metodo che recupera tutti gli Hotel, li ordina per nome e li serializza in file JSON.
     */
    public void run() {
        // Converti la mappa in una lista di hotel
        List<Hotel> hotelsList = new ArrayList<>(ServerMain.getHotels().values());
        // Ordinamento della lista di hotel per nome (utile soltanto per facilità di lettura del file di output).
        hotelsList.sort(Comparator.comparing(Hotel::getName));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(this.END_FILE_HOTELS_PATH).toAbsolutePath().normalize().toString()))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            // Serializzazione della lista in formato JSON.
            String jsonString = gson.toJson(hotelsList);
            // Scrittura del JSON nel file.
            writer.write(jsonString);
            System.out.println("Hotels saved on file");
        } catch (IOException ex) {
            System.err.println("I/O error from hotelToJson function.");
        }
    }
}
