
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.*;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mindrot.jbcrypt.BCrypt;

public class ServerMain {

    public static final String Ip = ServerFileConfigurationReader.getIp();
    public static final int DEFAULT_PORT = ServerFileConfigurationReader.getPort();
    private static ConcurrentHashMap<String, Hotel> hotels; 
    private static ConcurrentHashMap<String, User> users = FilesJsonReader.getUsersFromJson(); // Lettura user da file oppure map vuota.
    private static ConcurrentHashMap<String, RankingStructure> rankings = new ConcurrentHashMap<>(); // Contiene una ranking structure per ogni città.
    private static final int N_THREAD = 5;
    private static ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(N_THREAD); // ThreadPool utilizzato per la scrittura su file di Hotels e Users.
    private static final int WAITING_SECONDS_RANKING_RECALCULATION = Integer.parseInt(ServerFileConfigurationReader.get_Waiting_Seconds_Ranking_Recalculation()); // Minuti che intervallano i task di scrittura su file di Hotel e Users.
    private static final int WAITING_SECONDS_FILE_UPDATE = Integer.parseInt(ServerFileConfigurationReader.get_Waiting_Seconds_File_Update());
    private static AtomicBoolean stop = new AtomicBoolean(false);


    /**
    *  Parsing del file Hotel.json e riempimento ConcurrentHashMap hotels.
    */
    private static void hotelsInitialization(){
        try {
            ServerMain.hotels = FilesJsonReader.getHotelsFromJson();
        }
        catch (IOException e){
            System.err.println("Nessun file disponibile.");
        }
    }

    /**
    * Inizializza la ConcurrentHashMap rankings.
    */
    private static void rankingsInitialization() {
        for(Map.Entry<String, Hotel> entry : ServerMain.hotels.entrySet()) {
            rankings.putIfAbsent(entry.getValue().getCity(), new RankingStructure(entry.getValue().getCity()));
        }
    }
    
    public static ConcurrentHashMap<String, Hotel> getHotels() {
        return ServerMain.hotels;
    }
    
    public static ConcurrentHashMap<String, User> getUsers() {
        return ServerMain.users;
    }
    
    public static ConcurrentHashMap<String,RankingStructure> getRankings () {
        return ServerMain.rankings;
    }

    /**
    * Restituisce l'hotel associato ad un determinato id.
    *
    * @param id id del relativo hotel.
    * @return l'hotel associato a 'id'.
    */
    public static Hotel getHotelFromId(String id) {
        return ServerMain.hotels.get(id);
    }

    /**
     * Restituisce una concurrentHashMap di tutti gli hotel situati in una determinata città.
     *
     * @param city il nome della città per la quale filtrare gli hotel. Assume che {@code city} sia non
     *        null e corrispoinda esattamente al nome della città desiderata.
     * @return una nuova CuncurrentHashMap che contiene come chiavi gli identificatori univoci degli hotel
     *         e come valori tutti gli oggetti di tipo Hotel corrispondenti situati nella città specificata.
     */
    public static ConcurrentHashMap<String, Hotel> getHotelsOfCity(String city) {
        ConcurrentHashMap<String, Hotel> hotelsOfCity = new ConcurrentHashMap<>();
        for(Map.Entry<String, Hotel> entry : ServerMain.hotels.entrySet()) {
            if(entry.getValue().getCity().equals(city))
                hotelsOfCity.put(entry.getKey(), entry.getValue());
        }
        return hotelsOfCity;
    }

    /**
     * Configura una SelectionKey per operazioni di scrittura e aggiorna l'oggetto allegato.
     *
     * @param output il risultato dell'operazione che è stata effettuata precedentemente. Se è -5 significa che
     *               il client ha chiuso la connessione durante l'esecuzione dell'operazione e quindi la chiave
     *               viene cancellata.
     * @param operation l'identificativo dell'operazione che è stata effettuata precedentemente.
     * @param key   la SelectionKey da configurare per la scrittura o annulare.
     */
    private static void setWritableKey(int output, int operation, SelectionKey key){
        if(output == -5) // Il client ha chiuso il canale
            key.cancel();
        else {
            ObjectAttach objectAttach = (ObjectAttach) key.attachment();
            key.interestOps(SelectionKey.OP_WRITE);
            objectAttach.setOperation(operation);
            objectAttach.setOutput(output);
        }
    }

    /**
     * Legge un intero dal SocketChannel del client.
     *
     * @param client il SocketChannel del client da cui leggere l'intero.
     * @return l'intero letto dal client o -5 se il canale è stato chiuso durante la lettura.
     * @throws IOException
     */
    private static int readIntegerFromClient(SocketChannel client) throws IOException {
        try{
            ByteBuffer inputBuffer = ByteBuffer.allocate(Integer.BYTES);
            // Ricevo il codice dal server.
            int nBytesRead = client.read(inputBuffer);
            // Controllo che il canale non sia stato chiuso.
            if(nBytesRead == -1 || !client.isOpen()) {
                client.close();
                return -5;
            }
            // Controllo di aver ricevuto tutti i bytes che mi aspetto, altrimenti continuo a leggere.
            while (inputBuffer.hasRemaining()) {
                nBytesRead += client.read(inputBuffer);
                // Controllo che il canale non sia stato chiuso.
                if (nBytesRead == -1 || !client.isOpen()) {
                    client.close();
                    return -5;
                }
            }
            inputBuffer.flip();
            // ritorno l'intero ricevuto
            return inputBuffer.getInt();
        }
        catch (IOException e) {
            throw new IOException("Error reading integer from client.");
        }
    }

    /**
     * Legge una sequenza di byte da un SocketChannel client.
     *
     * @param client il SocketChannel del client da cui leggere la sequenza di byte.
     * @param nByteToRead il numero di bytes da leggere dal canale.
     * @return un array di bytes contenente i dati letti. Se si verifica un errore viene
     *         restituito un array di byte di lunghezza 1.
     * @throws IOException
     */
    private static byte[] readByteStringFromClient(SocketChannel client, int nByteToRead) throws IOException {
        try{
            ByteBuffer inputBuffer = ByteBuffer.allocate(nByteToRead);
            // Ricevo il codice dal server.
            int nBytesRead = client.read(inputBuffer);
            // Controllo che il canale non sia stato chiuso.
            if(nBytesRead == -1 || !client.isOpen()) {
                client.close();
                return new byte[1];
            }
            // Controllo di aver ricevuto tutti i bytes che mi aspetto, altrimenti continuo a leggere.
            while (inputBuffer.hasRemaining()) {
                nBytesRead += client.read(inputBuffer);
                if (nBytesRead == -1 || !client.isOpen()) {
                    client.close();
                    return new byte[1];
                }       
            }
            // Prendo i byte dal ByteBuffer
            inputBuffer.flip();
            byte[] bytesReceived = new byte[nByteToRead];
            inputBuffer.get(bytesReceived);
            // Ritorno i bytes ricevuti
            return bytesReceived;
        }
        catch (IOException e) {
            throw new IOException("Error reading a sequence of bytes from client.");
        }
    }

    // Scrive l'output di una richiesta (intero) al client e mette la key in OP_READ.

    /**
     * Scrive un intero al SocketChannel del client.
     *
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @throws IOException
     * @throws ClosedChannelException
     */
    private static void writeIntToClient (SocketChannel client, SelectionKey key) throws IOException, ClosedChannelException {
        ObjectAttach objectAttach = (ObjectAttach) key.attachment();
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        //System.out.println("[writeIntToClient] inviato intero: " + objectAttach.getOutput());
        buffer.putInt(objectAttach.getOutput());
        buffer.flip();
        int bytesWritten = 0;
        try {
            while (buffer.hasRemaining()) {
                bytesWritten = client.write(buffer);    
            }
        } catch (ClosedChannelException cce) {
            // Il canale è chiuso dal lato client
            client.close();
            throw new ClosedChannelException();
        }
        catch (IOException ex) {
            client.close();
            throw new IOException("Problem with the connection from client");
        }
        //System.out.println("[writeIntToClient] scritti numero byte: " + bytesWritten);
        // Se il canale è aperto rimetto la chiave in OP_READ, altrimenti elimino la chiave.
        if(client.isOpen())
            key.interestOps(SelectionKey.OP_READ);
        else
            key.cancel();
    }

    /**
     * Scrive un intero e una stringa al client attraverso un SocketChannel. Metodo utilizzato specificatamente
     *        per inviare al client il risultato dell'operazione e, se andata a buon fine, una stringa
     *        rappresentante le informazioni di un Hotel o di un insieme di Hotel.
     *
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @throws IOException
     * @throws ClosedChannelException
     */
    private static void writeIntAndStringToClient (SocketChannel client, SelectionKey key) throws IOException, ClosedChannelException {
        // Se l'operazione è andata a buon fine in objectAttach è presente la stringa da inviare al client.
        ObjectAttach objectAttach = (ObjectAttach) key.attachment();
        int nBytesToSend = Integer.BYTES;
        if(objectAttach.getOutput() == 0)
            nBytesToSend += Integer.BYTES + objectAttach.getMessagge().getBytes().length;
        //("Bytes della stringa: " + objectAttach.getMessagge().getBytes().length);
        //System.out.println("Bytes da inviare al client: " + nBytesToSend);
        // searchHotel o searchHotels è andata a buon fine: devo inviare sia il risultato dell'operazione che una stringa.
        if(nBytesToSend>4) {
            ByteBuffer outputBuffer = ByteBuffer.allocate(nBytesToSend);
            // Inserisco sul outputBuffer l'output di searchHotel o searchHotels.
            outputBuffer.putInt(objectAttach.getOutput());
            // Inserisco sul outputBuffer la lunghezza della stringa che rappresenta l'hotel o l'insieme di hotel richiesto.
            outputBuffer.putInt(objectAttach.getMessagge().getBytes().length);
            // Inserisco sul outputBuffer la stringa che rappresenta l'hotel o l'insieme di hotel richiesto.
            outputBuffer.put(objectAttach.getMessagge().getBytes());
            // Preparo l'outputBuffer al'invio dei dati.
            outputBuffer.flip();
            int bytesWritten = 0;
            // Scrivo il contenuto del buffer al client.
            try {
                while (outputBuffer.hasRemaining()) {
                    bytesWritten = client.write(outputBuffer);
                }
                //System.out.println("[writeIntToClient] scritti numero byte: " + bytesWritten);
            } catch (ClosedChannelException cce) {
                // Il canale è chiuso dal lato client
                client.close();
                throw new ClosedChannelException();
            } catch (IOException ex) {
                client.close();
                throw new IOException("Problem with the connection from client");
            }
            key.interestOps(SelectionKey.OP_READ);
        }
        // L'operazione non è NON andata a buon fine: devo inviare solo il risultato dell'operazione.
        else{ 
            ServerMain.writeIntToClient(client, key);
        }

    }

    /**
     * Metodo che gestisce la registrazione di un nuovo utente ricevendo username e password dal
     * client, ne verifica l'univocità e li memorizza. La password viene ricevuta in chiaro e poi
     * viene memorizzata criptata usando BCrypt.
     *
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @return int Un codice che rappresenta lo stato della registrazione:
     *              0 la registrazione è avvenuta con successo.
     *              1 l'username è già in uso.
     *             -1 se si verifica un errore di I/O.
     *             -5 se la connessione è stata chiusa improvvisamente.
     */
    private static int registration(SocketChannel client, SelectionKey key) {
        // Ricezione username e password
        try{
            String username = "";
            String hashedPassword = "";
            int i = 0; // Necessario per distinguere quale dei due dati sto processando: 0 username, 1 password.
            
            // Due cicli: primo ciclo leggo l'username, secondo ciclo leggo la password.
            while(i<2) {
                // Ricevo dal client l'intero (4 byte) che mi identifica la lunghezza della stringa.
                int string_length = ServerMain.readIntegerFromClient(client);
                // Se il canale è stato chiuso esco.
                if(string_length == -5) return -5;
                // Prendo i byte dal ByteBuffer e li metto in un array di byte per convertirli in stringa.
                byte[] stringBytes = new byte[string_length];
                stringBytes = ServerMain.readByteStringFromClient(client, string_length);
                // Se il canale è stato chiuso esco.
                if(stringBytes.length == 1 && stringBytes[0] == 0) return -5;
                // Conversione in stringa
                if(i == 0) {
                    username = new String(stringBytes);
                    i++;
                }
                else {
                    hashedPassword = BCrypt.hashpw(new String(stringBytes), BCrypt.gensalt()); 
                    i++;
                }
                
            }
            
            // Aggiungo il client all'interno della CuncurrentHashMap dei clients
            if(ServerMain.users.putIfAbsent(username, new User (username, hashedPassword)) != null) 
                return 1;

            ObjectAttach objectAttach = new ObjectAttach();
            key.attach(objectAttach);
  
        } catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -1;
        }

        return 0;
    
    }

    /**
     * Metodo che gestisce il login di un utente. Prima controlla che l'utente non sia già loggato,
     * poi legge username e password inviati dal client e infine veirifica l'esistenza dell' username
     * nel sistema e confronta la password ricevuta con quella hashata sul file.
     *
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @return int Un codice che rappresenta lo stato del login:
     *              0 la registrazione è avvenuta con successo.
     *             -1 se l'utente è già loggato.
     *             -2 se l'username non esiste.
     *             -3 se la password è errata.
     *             -4 se si verifica un errore di I/O.
     *             -5 se la connessione è stata chiusa improvvisamente.
     */
    private static int login (SocketChannel client, SelectionKey key) {
        
        String username = "";
        String password = "";
        // Recupero l'attach.
        ObjectAttach objectAttach = null;
        if(key.attachment() != null)
            objectAttach = (ObjectAttach) key.attachment();
        else {
            objectAttach = new ObjectAttach();
            key.attach(objectAttach);
        }
        // Controllo che l'utente non si loggato
        if(objectAttach.getUsername().isEmpty()) {
            // L'utente non è loggato, ricezione username e password.
            try{
                int i = 0; // Necessario per distinguere quale dei due dati sto processando: 0 username, 1 password.

                // Due cicli: primo ciclo leggo l'username, secondo ciclo leggo la password.
                while(i<2) {
                    // Ricevo dal client l'intero (4 byte) che mi identifica la lunghezza della stringa.
                    int string_length = ServerMain.readIntegerFromClient(client);
                    // Se il canale è stato chiuso esco.
                    if(string_length == -5) return -5;
                    // Ricevo dal client i byte che rappresentano la stringa.
                    byte[] stringBytes = new byte[string_length];
                    stringBytes = ServerMain.readByteStringFromClient(client, string_length);
                    // Se il canale è stato chiuso esco.
                    if(stringBytes.length == 1 && stringBytes[0] == 0) return -5;
                    // Conversione in stringa
                    if(i == 0) {
                        username = new String(stringBytes);
                        i++;
                    }
                    else {
                        password = new String(stringBytes);
                        i++;
                    }

                }
                // Errore: l'username non esiste. 
                if (! ServerMain.users.containsKey(username))
                    return -2;
                // Errore: password sbagliata.
                if(! BCrypt.checkpw(password, ServerMain.users.get(username).getHashedPassword())) 
                    return -3;
            }
            catch (IOException ex) {
                System.err.println("I/O error from registration function.");
                return -4;
            }
        }
        else {
            // Errore: l'utente è già loggato.
            return -1;
        }
        
        // Login effettuato correttamente.
        objectAttach.setUsername(username);
        return 0;
    }

    /**
     * Metodo che gestisce il logout di un utente connesso tramite SocketChannel. Prima verifica
     * se l'utente è già loggato, poi legge il nome utente inviato dal client e lo confronta con
     * quello memorizzato nell'attachment della SelectionKey, infine effettua il logout.
     *
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @return int Un intero che rappresenta lo stato del tentativo di logout:
     *             0 se il logout è stato completato con successo.
     *            -1 se l'utente non ha fatto precedentemente il login.
     *            -2 se l'username fornito non corrisponde a quello con cui l'utente era loggato.
     *            -3 se si verifica un errore di I/O
     *            -5 se la connessione è stata chiusa improvvisamente.
     */
    private static int logout (SocketChannel client, SelectionKey key) {
        String username = "";
        try{
            // Leggo l'intero (4 byte) che mi identifica la lunghezza della stringa.
            int string_length = ServerMain.readIntegerFromClient(client);
            // Se il canale è stato chiuso esco.
            if(string_length == -5) return -5;
            // // Ricevo dal client i byte che rappresentano la stringa..
            byte[] stringBytes = new byte[string_length];
            stringBytes = ServerMain.readByteStringFromClient(client, string_length);
            // Se il canale è stato chiuso esco.
            if(stringBytes.length == 1 && stringBytes[0] == 0) return -5;
            // Conversione in stringa
            username = new String(stringBytes);
        }
        catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -3;
        }

        // Controllo che l'utente non sia già loggato.
        if(key.attachment() == null) {
            key.attach(new ObjectAttach());
            return -1; // L'utente non è loggato.
        }
        
        ObjectAttach objectAttach = (ObjectAttach) key.attachment();
        
        // Errore: l'username non è lo stesso con cui si è fatto login.
        //System.out.println(objectAttach.getUsername()+ username);
        if(!objectAttach.getUsername().equals(username)){
            return -2;
        }
        // L'username è lo stesso con cui si è fatto login.
        return 0;
    }

    /**
     * Metodo che cerca un hotel specifico basandosi sul nome e sulla città dell'hotel stesso.
     * Riceve due stringhe dal client: prima il nome dell'hotel poi la città, fa la ricerca
     * nell'insieme degli hotel, se lo trova salva le informazioni nella attachment della
     * SelectionKey.
     *
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @return int Un intero che indica il risultato dell'operazione:
     *             0 se l'hotel è stato trovato con successo.
     *            -1 se non viene trovato alcun hotel ai criteri di ricerca.
     *            -2 per errori di I/O.
     *            -5 se la connessione è stata chiusa improvvisamente.
     */
    private static int searchHotel (SocketChannel client, SelectionKey key) {
        String hotelName = "";
        String city = "";
        ObjectAttach objectAttach = null;
        if(key.attachment() != null)
            objectAttach = (ObjectAttach) key.attachment();
        else
            objectAttach = new ObjectAttach();
        try{
            int i = 0; // Necessario per distinguere quale dei due dati sto processando: 0 hotelName, 1 città.

            // Due cicli: primo ciclo leggo il hotelName, secondo ciclo leggo la citta.
            while(i<2) {
                // Ricevo dal client l'intero (4 byte) che mi identifica la lunghezza della stringa.
                int string_length = ServerMain.readIntegerFromClient(client);
                // Se il canale è stato chiuso esco.
                if(string_length == -5) return -5;
                // Ricevo dal client i byte che rappresentano la stringa.
                byte[] stringBytes = new byte[string_length];
                stringBytes = ServerMain.readByteStringFromClient(client, string_length);
                // Se il canale è stato chiuso esco.
                if(stringBytes.length == 1 && stringBytes[0] == 0) return -5;
                // Conversione in stringa
                if(i == 0) {
                    hotelName = new String(stringBytes);
                    //System.out.println("Nome hotel: '" + hotelName + "'.");
                    i++;
                }
                else {
                    city = new String(stringBytes);
                    //System.out.println("Citta: '" + city + "'.");
                    i++;
                }
            }
            
            // Ricerca dell'hotel
            for (Map.Entry<String, Hotel> entry : ServerMain.getHotelsOfCity(city).entrySet()) {
                if(entry.getValue().getName().equals(hotelName)) {
                    String temp = entry.getValue().toString();
                    objectAttach.setMessagge(temp);
                    break;
                }
            }
        }
        catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -2;
        }
        
        // Se l'utente non era loggato aggiungo l'attach.
        if(key.attachment() == null) {
            key.attach(objectAttach);
        }
        
        // ERRORE: non esiste nessun hotel con quel nome in quella città.
        if(objectAttach.getMessagge().length()<=1)
            return -1;
        
        return 0;
    }

    /**
     * Il metodo ricerca e inserisce nell'attachment della SelectionKey una
     * classifica degli hotel in base alla città specificata dal client.
     * Il metodo legge una stringa che rappresenta il nome
     * della città dal client tramite SocketChannel. Utilizza questa città per
     * recuperare la classifica degli hotel.
     *
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @return int Un intero che indica il risultato dell'operazione:
     *             0 se la classifica è stata trovata con successo.
     *            -1 se non esiste alcuna classifica per quella città.
     *            -2 per errori di I/O.
     *            -5 se la connessione è stata chiusa improvvisamente.
     * @throws IOException
     */
    private static int searchHotels (SocketChannel client, SelectionKey key) throws IOException {
        String city = "";
        ObjectAttach objectAttach = null;
        if(key.attachment() != null)
            objectAttach = (ObjectAttach) key.attachment();
        else
            objectAttach = new ObjectAttach();
        try{            
            // Ricevo dal client l'intero (4 byte) che mi identifica la lunghezza della stringa.
            int string_length = ServerMain.readIntegerFromClient(client);
            // Se il canale è stato chiuso esco.
            if(string_length == -5) return -5;
            // Ricevo dal client l'insieme di byte che mi identifica la stringa.
            byte[] stringBytes = new byte[string_length];
            stringBytes = ServerMain.readByteStringFromClient(client, string_length);
            // Se il canale è stato chiuso esco.
            if(stringBytes.length == 1 && stringBytes[0] == 0) return -5;
            // Conversione in stringa
            city = new String(stringBytes);
            
            RankingStructure temp= ServerMain.rankings.get(city);
            // Se la città esiste prendo la classifica
            if(temp != null) {
                objectAttach.setMessagge(temp.toString());
            }

        }
        catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -2;
        }

        // Se l'utente non era loggato aggiungo l'attach.
        if(key.attachment() == null) {
            key.attach(objectAttach);
        }
        
        // ERRORE: non esiste nessun hotel in quella città.
        if(objectAttach.getMessagge().isEmpty())
            return -1;

        return 0;
    }

    /**
     * Questo metodo gestisce l'inserimento di una recensione da parte di
     * un utente. Riceve dal client il nome, la città dell'hotel e una
     * serie di interi che rappresentano la recensione tramite
     * SocketChannel. Aggiunge la recensione al relativo oggetto Hotel e
     * incrementa il numero di recensioni per quel utente.
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @return int Un intero che indica il risultato dell'operazione:
     *             0 se la recensione è stata inserita con successo.
     *            -1 se l'utente tenta di eseguire l'operazione senza essere loggato.
     *            -2 per errori di I/O.
     *            -3 hotel inesistente.
     *            -5 se la connessione è stata chiusa improvvisamente.
     * @throws IOException
     */
    private static int insertReview (SocketChannel client, SelectionKey key) throws IOException {
        String hotelName = "";
        String city = "";
        int[] scores = new int[5];
        // Recupero il vecchio ObjectAttach
        ObjectAttach objectAttach = (ObjectAttach) key.attachment();
        
        try{
            int i = 0; // Necessario per distinguere quale dei due dati sto processando: 0 nome hotel, 1 citta.
            // Due cicli: primo ciclo leggo il nome dell'hotel, secondo ciclo leggo la citta.
            while(i<2) {
                // Ricevo dal client l'intero (4 byte) che mi identifica la lunghezza della string.
                int string_length = ServerMain.readIntegerFromClient(client);
                if(string_length == -5) return -5;
                // Ricevo dal client l'insieme di byte che mi identifica la stringa.
                byte[] stringBytes = new byte[string_length];
                stringBytes = ServerMain.readByteStringFromClient(client, string_length);
                // Se il canale è stato chiuso esco.
                if(stringBytes.length == 1 && stringBytes[0] == 0) return -5;
                // Conversione in stringa
                if(i == 0)
                    hotelName = new String(stringBytes);
                else
                    city = new String(stringBytes);
                i++;
            }
            // Ricezione dei voti che l'utente ha assegnato all'hotel
            for(i=0; i<5; i++) {
                scores[i] = ServerMain.readIntegerFromClient(client);
            }
            
            // Controllo che l'utente sia loggato.
            if(key.attachment() == null)
                return -1; // L'utente non è loggato.

            // Controllo che la città sia presente
            if(ServerMain.getRankings().containsKey(city)) {
                // Ricerca dell'id dell'hotel a cui deve essere aggiunta la recensione
                String idHotel = "";
                for (Map.Entry<String, Hotel> entry : ServerMain.getHotelsOfCity(city).entrySet()) {
                    if (entry.getValue().getCity().equals(city) && entry.getValue().getName().equals(hotelName)) {
                        idHotel = entry.getValue().getId();
                        break;
                    }
                }
                if (idHotel.isEmpty())
                    return -3; // Hotel inesistente.

                // Utilizzo compute() per aggiornare una parte specifica dell'hotel
                ServerMain.hotels.compute(idHotel, (id, hotel) -> {
                    if (hotel != null) {
                        // Aggiungo la recensione all'insieme di recensioni per quell'hotel.
                        hotel.addReview(scores);
                    }
                    // Restituisci l'hotel, modificato o non modificato
                    return hotel;
                });

                // Incremento il numero di recensioni per quel user
                ServerMain.users.get(objectAttach.getUsername()).addRecensione();
            }
            else
                return -3; // hotel inesistente.
        }
        catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -2;
        }

        return 0; // Successo
    }

    /**
     * Questo metodo gestisce la richiesta del badge da parte dell'utente.
     * Il metodo riceve l'username dal client tramite SocketChannel, recupera il badge
     * associato a quell'username e lo mette nell'attach della SelectionKey.
     *
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @return int Un intero che indica il risultato dell'operazione:
     *             0 se il badge è stato recuperato con successo.
     *            -1 se l'utente tenta di visualizzare il badge senza essere loggato.
     *            -5 se la connessione è stata chiusa improvvisamente.
     * @throws IOException
     */
    private static int showBadge (SocketChannel client, SelectionKey key) throws IOException {
        // Ricevo l'username dal client.
        int usernameLength = ServerMain.readIntegerFromClient(client);
        if(usernameLength == -5) return -5;
        byte[] byteUsername = ServerMain.readByteStringFromClient(client, usernameLength);
        // Se il canale è stato chiuso esco.
        if(byteUsername.length == 1 && byteUsername[0] == 0) return -5;
        String username = new String (byteUsername);
        // Controllo che l'utente non sia loggato.
        if(key.attachment() == null)
            return -1; // L'utente non è loggato.
        ObjectAttach objectAttach = (ObjectAttach) key.attachment();
        if(objectAttach.getUsername().isEmpty() || ! objectAttach.getUsername().equals(username))
            return -1; // L'utente non è loggato o è loggato con username diverso.
        else { 
            // L'utente è loggato quindi è sicuramente presente dentro clients: recupero il badge per quell'utente.
            User user = ServerMain.users.get(objectAttach.getUsername());
            String badge = user.getBadge();
            objectAttach.setMessagge(badge);
        }
        // Operazione andata a buon fine.
        return 0;
    }

    /**
     * Questo metodo si preoccupa di chiudere la connessione con un client.
     * Si occupa di eliminare la selection key associata al client e di chiudere
     * il SocketChannel.
     * @param client il SocketChannel del client a cui scrivere l'intero.
     * @param key la SelectionKey associata al canale del client.
     * @throws IOException
     */
    private static void closeConnection(SocketChannel client, SelectionKey key) throws IOException{
        key.cancel();
        client.close();
    }
    
    public static void main(String[] args) {
        // Parsing del file Hotel.json e riempimento di hotels.
        ServerMain.hotelsInitialization();
        // Inizializzazione strutture dati per il ranking.
        ServerMain.rankingsInitialization();
        // Inserimento nel ThreadPool dei task di scrittura su file di Hotels e Users
        ServerMain.scheduledThreadPool.scheduleWithFixedDelay(new HotelsToJsonTask(), WAITING_SECONDS_FILE_UPDATE, WAITING_SECONDS_FILE_UPDATE, TimeUnit.SECONDS);
        ServerMain.scheduledThreadPool.scheduleWithFixedDelay(new UsersToJsonTask(), WAITING_SECONDS_FILE_UPDATE, WAITING_SECONDS_FILE_UPDATE, TimeUnit.SECONDS);
        ServerMain.scheduledThreadPool.scheduleWithFixedDelay(new UpdateRankingsTask(), 0, WAITING_SECONDS_RANKING_RECALCULATION, TimeUnit.SECONDS);
        /*RICORDATI DI CAMBIARE DA SECONDS a MINUTES*/
        ServerSocketChannel serverChannel;
        Selector selector;
        try {
            serverChannel = ServerSocketChannel.open();
            ServerSocket ss = serverChannel.socket();
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(ServerMain.Ip), ServerMain.DEFAULT_PORT);
            ss.bind(address);
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            // Partenza del thread per terminare il server
            Scanner scanner = new Scanner(System.in);
            Thread listeningStopRequest = new Thread(new StopServerTask(ServerMain.stop, scanner, selector));
            listeningStopRequest.start();
        
            while (!ServerMain.stop.get()) {
                try {
                    selector.select();
                } 
                catch (IOException ex) {
                    ex.printStackTrace();
                    break;
                }

                Set <SelectionKey> readyKeys = selector.selectedKeys();
                Iterator <SelectionKey> iterator = readyKeys.iterator();

                while (iterator.hasNext() && !ServerMain.stop.get()) {
                    SelectionKey key = iterator.next();
                    iterator.remove(); // rimuove la chiave dal Selected Set, ma non dal Registered Set
                    try {
                        if (key.isAcceptable()) {
                            ServerSocketChannel server = (ServerSocketChannel) key.channel();
                            SocketChannel client = server.accept();
                            System.out.println("Accepted connection from " + client);
                            client.configureBlocking(false);
                            // Dopo aver accettato la connessione registro il client per la lettura 
                            SelectionKey key2 = client.register(selector,SelectionKey.OP_READ);
                        }
                        else if(key.isReadable()){
                            SocketChannel client = (SocketChannel) key.channel();
                            int operation = ServerMain.readIntegerFromClient(client);
                            if(operation == -5) { // Il client ha chiuso il canale mentre il server leggeva l'operazione che voleva effettuare.
                                client.close(); // Chiudo il canale
                                break;
                            }
                            //System.out.println("L'operazione che vuole effettuare il client è la: "+ operation);

                            switch (operation) {
                                case 1: int output = ServerMain.registration(client, key);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key);
                                        break;

                                case 2: output = ServerMain.login(client, key);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key);
                                        break;

                                case 3: output = ServerMain.logout(client, key);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key);
                                        break;

                                case 4: output = ServerMain.searchHotel(client, key);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key);
                                        break;

                                case 5: output = ServerMain.searchHotels(client, key);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key);
                                        break;

                                case 6: output = ServerMain.insertReview(client, key);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key);
                                        break;

                                case 7: output = ServerMain.showBadge(client, key);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key);
                                        break;
                                
                                case 8: ServerMain.closeConnection(client, key);
                                        break;

                            }

                        }
                        else if (key.isWritable()) { 
                            SocketChannel client = (SocketChannel) key.channel();
                            ObjectAttach objectAttach = (ObjectAttach) key.attachment();

                            // Quale operazione è stata effettuata da quel client.
                            switch (objectAttach.getOperation()){

                                case 1: try{
                                            // Scrive l'output dell'operazione (intero) al client e mette la key in OP_READ.
                                            ServerMain.writeIntToClient(client, key);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata
                                        catch (ClosedChannelException cce) {
                                            break;
                                        }
                                        catch (IOException e) {
                                            break;
                                        }
                                        key.attach(null);
                                        break;

                                case 2: try{
                                            // Scrive l'output dell'operazione (intero) al client e mette la key in OP_READ.
                                            ServerMain.writeIntToClient(client, key);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata.
                                        catch (ClosedChannelException cce) {
                                            break;
                                        }
                                        catch (IOException e) {
                                            break;
                                        }
                                        // Se il login è andato a buon fine metto l'attach altrimenti null.
                                        if(objectAttach.getOutput() == 0) 
                                            key.attach(objectAttach);
                                        else
                                            key.attach(null);
                                        break;

                                case 3: try{
                                            // Scrive l'output dell'operazione (intero) al client e mette la key in OP_READ.
                                            ServerMain.writeIntToClient(client, key);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata.
                                        catch (ClosedChannelException cce) {
                                            break;
                                        }
                                        catch (IOException e) {
                                            break;
                                        }
                                        // Dopo il logout metto null come attach se l'operazione è andata a buon fine.
                                        if(objectAttach.getOutput() == 0 || objectAttach.getOutput() == -1)
                                            key.attach(null);
                                        else
                                            key.attach(objectAttach);
                                        break;
                                        
                                case 4: try{
                                            // Scrive l'output della richiesta e l'hotel, se presente, al client; in più mette la key in OP_READ.
                                            ServerMain.writeIntAndStringToClient(client, key);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata.
                                        catch (IOException e) {
                                            break;
                                        }
                                        // Gestione dell'attach.
                                        if(objectAttach.getOutput() == 0) { // L'operazione è andata a buon fine.
                                            if(objectAttach.getUsername().isEmpty()) // L'utente non era loggato.
                                                key.attach(null);
                                            else { // L'utente era loggato.
                                                objectAttach.setMessagge("");
                                                key.attach(objectAttach);
                                            }
                                        }
                                        else // L'operazione non è andata a buon fine
                                            key.attach(objectAttach);
                                        break;
                                        
                                case 5: try{
                                            // Scrive l'output della richiesta e gli hotels, se presenti, al client; in più mette la key in OP_READ.
                                            ServerMain.writeIntAndStringToClient(client, key);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata.
                                        catch (IOException e) {
                                            break;
                                        }
                                        // Gestione dell'attach.
                                        if(objectAttach.getOutput() == 0) { // L'operazione è andata a buon fine.
                                            if(objectAttach.getUsername().isEmpty()) // L'utente non era loggato.
                                                key.attach(null);
                                            else { // L'utente era loggato.
                                                objectAttach.setMessagge("");
                                                key.attach(objectAttach);
                                            }
                                        }
                                        else // L'operazione non è andata a buon fine
                                            key.attach(objectAttach);
                                        break;
                                        
                                case 6: try{
                                            // Scrive l'output della richiesta
                                            ServerMain.writeIntToClient(client, key);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata.
                                        catch (IOException e) {
                                            break;
                                        }
                                        key.attach(objectAttach);
                                        break;

                                case 7: try{
                                            // Scrive l'output della richiesta.
                                            ServerMain.writeIntAndStringToClient(client, key);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata.
                                        catch (IOException e) {
                                            break;
                                        }
                                        //key.interestOps(SelectionKey.OP_READ);
                                        key.attach(objectAttach);
                                        break;

                            }
                        }
                    } catch (IOException ex) { 
                        key.cancel();
                        try { key.channel().close(); }
                        catch (IOException cex) {} 
                    }
                
                }
            }

            try{
                // Aspetto la terminazione del StopServerTask
                listeningStopRequest.join();
            }
            catch (InterruptedException e){
                System.out.println("Errore chiusura thread StopServer.");
            }

            serverChannel.close();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        finally {
            // Chiusura dello scheduledThreadPool
            ServerMain.scheduledThreadPool.shutdown();
            try{
                if(!ServerMain.scheduledThreadPool.awaitTermination(5, TimeUnit.SECONDS))
                    scheduledThreadPool.shutdownNow();
            }
            catch (InterruptedException e) {
                scheduledThreadPool.shutdownNow();
            }
            System.out.println("Server terminated.");
        }
        
    }
}
