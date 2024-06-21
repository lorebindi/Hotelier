

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/*Classe che si preoccupa della lettura da/a file json*/
public class FilesJsonReader{
    // File di partenza fornito.
    private final static String START_FILE_HOTELS_PATH = ServerFileConfigurationReader.getStartFileHotelsPath();
    // File di hotel utilizzato in cui per ogni hotel sono specificate tutte le recensioni.
    private final static String END_FILE_HOTELS_PATH = ServerFileConfigurationReader.getEndFileHotelsPath();
    private final static String FILE_CLIENTS_PATH = ServerFileConfigurationReader.getFileUsersPath();

    /**
     * Metodo che legge le informazioni sugli hotel da un file JSON e li inserisce
     * in una ConcurrentHashMap. Prima tenta di leggere gli hotel dal file principale endHotel.json,
     * se fallisce legge dal file di backup Hotel.json.
     * @return Una ConcurrentHashMap contenente coppie composte da l'Id dell'hotel e il relativo
     *          oggetto Hotel.
     * @throws IOException
     */
    public static ConcurrentHashMap<String,Hotel> getHotelsFromJson() throws IOException {
        BufferedReader reader = null;
        try {
            // Apro un BufferedReader per leggere dal file
            reader = Files.newBufferedReader(Paths.get(FilesJsonReader.END_FILE_HOTELS_PATH).toAbsolutePath().normalize());
            // Leggo gli hotel dal file json e li inserisco in un ArrayList.
            ArrayList<Hotel> hotelsArray = new Gson().fromJson(reader, new TypeToken<List<Hotel>>() {}.getType());
            if(hotelsArray == null || hotelsArray.isEmpty()) {
                throw new IOException("Empty file");
            }
            else {
                // Inserisco gli hotel nella ConcurrentHashMap
                ConcurrentHashMap<String, Hotel> hotels = new ConcurrentHashMap<>(hotelsArray.size());
                for (Hotel hotel : hotelsArray) {
                    hotels.put(hotel.getId(), new Hotel(hotel.getId(), hotel.getName(), hotel.getDescription(),
                            hotel.getCity(), hotel.getPhone(), hotel.getServices(), hotel.getRatings()));
                }
                return hotels;
            }
        }
        catch (IOException ex) { // Se ho problemi ad aprire il primo file apro il secondo.
            try{
                // Apro un BufferedReader per leggere dal file
                reader = Files.newBufferedReader(Paths.get(FilesJsonReader.START_FILE_HOTELS_PATH).toAbsolutePath().normalize());
                // Leggo gli hotel dal file json e li inserisco in un ArrayList.
                ArrayList<StartHotel> hotelsArray = new Gson().fromJson(reader, new TypeToken<List<StartHotel>>() {}.getType());
                // Inserisco gli hotel nella ConcurrentHashMap
                ConcurrentHashMap<String,Hotel> hotels = new ConcurrentHashMap<>(hotelsArray.size());
                for(StartHotel startHotel : hotelsArray) {
                    hotels.put(startHotel.getId(), new Hotel(startHotel.getId(), startHotel.getName(), startHotel.getDescription(),
                            startHotel.getCity(),startHotel.getPhone(), startHotel.getServices()));
                }
                return hotels;
            }
            catch (IOException ex2) {
                throw new IOException();
            }
        }
    }

    /**
     * Metodo che legge le informazioni sugli utenti da un file JSON e li inserisce
     * in una ConcurrentHashMap. Tenta di leggere gli utenti dal file endHotel.json,
     * se fallisce restituisce una ConcurrentHashMap vuota.
     * @return Una ConcurrentHashMap contenente coppie composte dall'username dell'utente e il relativo
     *          oggetto User, altrimenti una CouncurrentHashMap vuota.
     * @throws IOException
     */
    public static ConcurrentHashMap<String,User> getUsersFromJson() {
        try {
            ConcurrentHashMap<String, User> users;
            // Apro un BufferedReader per leggere dal file
            BufferedReader reader = Files.newBufferedReader(Paths.get(FilesJsonReader.FILE_CLIENTS_PATH));
            // Leggo gli utenti dal file json e li inserisco in un ArrayList.
            ArrayList<User> usersArray = new Gson().fromJson(reader, new TypeToken<List<User>>() {}.getType());
            if(usersArray != null) {
                // Inserisco gli utenti nella ConcurrentHashMap
                users = new ConcurrentHashMap<>(usersArray.size());
                for (User user : usersArray) {
                    users.put(user.getUsername(), user);
                }
            }
            else
                users = new ConcurrentHashMap<>();
            return users;
        }
        catch (IOException ex) { 
            return new ConcurrentHashMap <String,User> ();
        }
    }
    
}
