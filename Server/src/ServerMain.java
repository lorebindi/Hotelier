
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


    // Parsing del file Hotel.json e riempimento di hotels.
    private static void hotelsInitialization(){
        try {
            ServerMain.hotels = FilesJsonReader.getHotelsFromJson();
        }
        catch (IOException e){
            System.err.println("Nessun file disponibile.");
        }
    }
    
    // Inizializza la ConcurrentHashMap rankings.
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
    
    // Restituisce l'hotel associato ad un determinato id.
    public static Hotel getHotelFromId(String id) {
        return ServerMain.hotels.get(id);
    }
    
    // Restituisce una concurrentHashMap che contiene tutti gli hotel di una determinata città.
    public static ConcurrentHashMap<String, Hotel> getHotelsOfCity(String city) {
        ConcurrentHashMap<String, Hotel> hotelsOfCity = new ConcurrentHashMap<>();
        for(Map.Entry<String, Hotel> entry : ServerMain.hotels.entrySet()) {
            if(entry.getValue().getCity().equals(city))
                hotelsOfCity.put(entry.getKey(), entry.getValue());
        }
        return hotelsOfCity;
    }
    
    // Costruisce il newAttach e mette la SelectionKey in writable.
    private static void setWritableKey(int output, int operation, SelectionKey key, ObjectAttach newAttach){
        if(output == -5) // Il client ha chiuso il canale
            key.cancel();
        else {
            key.interestOps(SelectionKey.OP_WRITE);
            newAttach.setOperation(operation);
            newAttach.setOutput(output);
            key.attach(newAttach);
        }
    }
    
    // Restituisce un intero ricevuto dal client tramite ByteBuffer.
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
    
    // Restituisce un array di byte ricevuti dal client, utilizza ByteBuffer.
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
    private static void writeIntToClient (SocketChannel client, SelectionKey key, ObjectAttach objectAttach) throws IOException, ClosedChannelException {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        System.out.println("[writeIntToClient] inviato intero: " + objectAttach.getOutput());
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
        System.out.println("[writeIntToClient] scritti numero byte: " + bytesWritten);
        // Se il canale è aperto rimetto la chiave in OP_READ, altrimenti elimino la chiave.
        if(client.isOpen())
            key.interestOps(SelectionKey.OP_READ);
        else
            key.cancel();
    }
    
    // Scrive un intero (output dell'operazione) e una stringa (hotel/hotels/badge) al client e mette la key in OP_READ.
    private static void writeIntAndStringToClient (SocketChannel client, SelectionKey key, ObjectAttach objectAttach) throws IOException, ClosedChannelException {
        // Se l'operazioni è andata a buon fine in objectAttach è presente la stringa da inviare al client.
        int nBytesToSend = Integer.BYTES;
        if(objectAttach.getOutput() == 0)
            nBytesToSend += Integer.BYTES + objectAttach.getMessagge().getBytes().length;
        System.out.println("Bytes della stringa: " + objectAttach.getMessagge().getBytes().length);
        System.out.println("Bytes da inviare al client: " + nBytesToSend);
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
                System.out.println("[writeIntToClient] scritti numero byte: " + bytesWritten);
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
        // searchHotel o searchHotels è NON andata a buon fine: devo inviare solo il risultato dell'operazione.
        else{ 
            ServerMain.writeIntToClient(client, key, objectAttach);
        }

    }
    
    // Return value: 0 okay, 1 username già usato, -1 errore del server, -5 il client ha chiuso il canale.
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
  
        } catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -1;
        }
        
        return 0;
    
    }
    
    // Return value: 0 okay, -1 già loggato, -2 username inesistente, -3 password errata, -4 errore server, -5 il client ha chiuso il canale.
    private static int login (SocketChannel client, SelectionKey key, ObjectAttach newAttach) {
        
        String username = "";
        String password = "";
        
        // Controllo che l'utente non sia già loggato.
        ObjectAttach oldAttach = (ObjectAttach) key.attachment();
        if(oldAttach == null || oldAttach.getUsername().length()<=1) {
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
        newAttach.setUsername(username);
        return 0;
    }
    
    // Return value: 0 okay, -1 l'utente non è loggato, -2 username fornito != username login, -3 server error, -5 il client ha chiuso il canale.
    private static int logout (SocketChannel client, SelectionKey key, ObjectAttach newAttach) {
        
        String username = "";

        // Controllo che l'utente non sia già loggato.
        if(key.attachment() == null)
            return -1; // L'utente non è loggato.
        
        // L'utente è loggato, ricezione username.
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
        
        ObjectAttach oldAttach = (ObjectAttach) key.attachment();
        
        // Errore: l'username non è lo stesso con cui si è fatto login.
        System.out.println(oldAttach.getUsername()+ username);
        if(!oldAttach.getUsername().equals(username)){
            // Mantengo l'username di login
            newAttach.setUsername(oldAttach.getUsername());
            return -2;
        }
        else {
            newAttach.setUsername(username);
        }
        // L'username è lo stesso con cui si è fatto login.
        return 0;
    }
    
    // Return value: 0 okay, -1 hotel inesistente, -2 server error.
    private static int searchHotel (SocketChannel client, SelectionKey key, ObjectAttach newAttach) {
        String nomeHotel = "";
        String citta = "";
        try{
            int i = 0; // Necessario per distinguere quale dei due dati sto processando: 0 nomeHotel, 1 città.

            // Due cicli: primo ciclo leggo il nomeHotel, secondo ciclo leggo la citta.
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
                    nomeHotel = new String(stringBytes);
                    System.out.println("Nome hotel: '" + nomeHotel + "'.");
                    i++;
                }
                else {
                    citta = new String(stringBytes); 
                    System.out.println("Citta: '" + citta + "'.");
                    i++;
                }
            }
            
            // Ricerca dell'hotel
            for (Map.Entry<String, Hotel> entry : ServerMain.hotels.entrySet()) {
                if(entry.getValue().getName().equals(nomeHotel) && entry.getValue().getCity().equals(citta)) {
                    String temp = entry.getValue().toString();
                    newAttach.setMessagge(temp);
                    break;
                }
            }
        }
        catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -2;
        }
        
        // Se l'utente era loggato mantengo l'informazione.
        if(key.attachment() != null) {
            ObjectAttach oldAttach = (ObjectAttach)key.attachment();
            newAttach.setUsername(oldAttach.getUsername());
        }
        
        // ERRORE: non esiste nessun hotel con quel nome in quella città.
        if(newAttach.getMessagge().length()<=1)
            return -1;
        
        return 0;
    }
    
    // Return value: 0 okay, -1 hotel inesistente, -2 server error.
    private static int searchHotels (SocketChannel client, SelectionKey key, ObjectAttach newAttach) throws IOException {
        String citta = "";
        String risp = "";
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
            citta = new String(stringBytes);
            
            RankingStructure temp= ServerMain.rankings.get(citta);
            // Se la città esiste prendo la classifica
            if(temp != null) {
                ArrayList<String> stringRanking = new ArrayList<>(ServerMain.rankings.get(citta).getRanking());
                stringRanking.sort(new ComparatoreHotel());
                StringBuilder sb = new StringBuilder();
                int i = 1;
                for(String idHotel : stringRanking){
                    sb.append(i).append(") ").append(ServerMain.getHotelFromId(idHotel).toString()).append("\n");
                    i++;
                }
                risp = sb.toString();
            }

        }
        catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -2;
        }
        
        // Se l'utente era loggato mantengo l'informazione.
        if(key.attachment() != null) {
            ObjectAttach oldAttach = (ObjectAttach)key.attachment();
            newAttach.setUsername(oldAttach.getUsername());
        }
        
        // ERRORE: non esiste nessun hotel in quella città.
        if(risp.isEmpty())
            return -1;
        
        newAttach.setMessagge(risp);
        
        return 0;
    }
    
    // Return value: 0 okay, -1 l'utente non è loggato, -2 server error.
    private static int insertReview (SocketChannel client, SelectionKey key, ObjectAttach newAttach) throws IOException {
        String nomeHotel = "";
        String citta = "";
        int[] scores = new int[5];
        // Recupero il vecchio ObjectAttach
        ObjectAttach oldAttach = (ObjectAttach) key.attachment();
        
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
                    nomeHotel = new String(stringBytes);
                else
                    citta = new String(stringBytes);
                i++;
            }
            // Ricezione dei voti che l'utente ha assegnato all'hotel
            for(i=0; i<5; i++) {
                scores[i] = ServerMain.readIntegerFromClient(client);
            }
            
            // Controllo che l'utente non sia loggato.
            if(key.attachment() == null)
                return -1; // L'utente non è loggato.
            
            // Ricerca dell'id dell'hotel a cui deve essere aggiunta la recensione
            String idHotel = "";
            for(Map.Entry<String, Hotel> entry : ServerMain.hotels.entrySet()) {
                if(entry.getValue().getCity().equals(citta) && entry.getValue().getName().equals(nomeHotel)) {
                    idHotel = entry.getValue().getId();
                    break;
                }
            }
            
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
            ServerMain.users.get(oldAttach.getUsername()).addRecensione();

        }
        catch (IOException ex) {
            System.err.println("I/O error from registration function.");
            return -2;
        }
        
        // L'utente rimane loggato.
        newAttach.setUsername(oldAttach.getUsername());
        return 0;
    }
    
    // Return value: 0 okay, -1 l'utente non è loggato.
    private static int showBadge (SocketChannel client, SelectionKey key, ObjectAttach newAttach) throws IOException {
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
        ObjectAttach oldAttach = (ObjectAttach) key.attachment();
        if(oldAttach.getUsername().isEmpty() || ! oldAttach.getUsername().equals(username))
            return -1; // L'utente non è loggato.
        else { 
            // L'utente è loggato quindi è sicuramente presente dentro clients: recupero il badge per quell'utente.
            User user = ServerMain.users.get(oldAttach.getUsername());
            String badge = user.getBadge();
            newAttach.setMessagge(badge);
            // L'utente rimane loggato.
            newAttach.setUsername(oldAttach.getUsername());
        }
        // Operazione andata a buon fine.
        return 0;
    }

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
        ServerMain.scheduledThreadPool.scheduleWithFixedDelay(new UpdateRankingsTask(), WAITING_SECONDS_RANKING_RECALCULATION, WAITING_SECONDS_RANKING_RECALCULATION, TimeUnit.SECONDS);
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
        
            while (true) {
                try {
                    selector.select(); // in questo caso è bloccante.
                } 
                catch (IOException ex) {
                    ex.printStackTrace();
                    break;
                }

                Set <SelectionKey> readyKeys = selector.selectedKeys();
                Iterator <SelectionKey> iterator = readyKeys.iterator();

                while (iterator.hasNext()) {
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
                            System.out.println("L'operazione che vuole effettuare il client è la: "+ operation);
                            // Rappresenta l'objectAttach che verrà riempito per la prossima operazione
                            ObjectAttach newAttach = new ObjectAttach();

                            switch (operation) {
                                case 1: int output = ServerMain.registration(client, key);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key, newAttach);
                                        break;

                                case 2: output = ServerMain.login(client, key, newAttach);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key, newAttach);
                                        break;

                                case 3: output = ServerMain.logout(client, key, newAttach);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key, newAttach);
                                        break;

                                case 4: output = ServerMain.searchHotel(client, key, newAttach);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key, newAttach);
                                        break;

                                case 5: output = ServerMain.searchHotels(client, key, newAttach);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key, newAttach);
                                        break;

                                case 6: output = ServerMain.insertReview(client, key, newAttach);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key, newAttach);
                                        break;

                                case 7: output = ServerMain.showBadge(client, key, newAttach);
                                        // Riempo l'objectAttach e setto la key a writable.
                                        ServerMain.setWritableKey(output, operation, key, newAttach);
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
                                            ServerMain.writeIntToClient(client, key, objectAttach);
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
                                            ServerMain.writeIntToClient(client, key, objectAttach);
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
                                            ServerMain.writeIntToClient(client, key, objectAttach);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata.
                                        catch (ClosedChannelException cce) {
                                            break;
                                        }
                                        catch (IOException e) {
                                            break;
                                        }
                                        // Dopo il logout metto null come attach se l'operazione è andata a buon fine.
                                        if(objectAttach.getOutput() == 0)
                                            key.attach(null);
                                        else
                                            key.attach(objectAttach);
                                        break;
                                        
                                case 4: try{
                                            // Scrive l'output della richiesta e l'hotel, se presente, al client; in più mette la key in OP_READ.
                                            ServerMain.writeIntAndStringToClient(client, key, objectAttach);
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
                                            ServerMain.writeIntAndStringToClient(client, key, objectAttach);
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
                                            ServerMain.writeIntToClient(client, key, objectAttach);
                                        }
                                        // Problemi sulla connessione, la key è stata cancellata.
                                        catch (IOException e) {
                                            break;
                                        }
                                        key.attach(objectAttach);
                                        break;

                                case 7: try{
                                            // Scrive l'output della richiesta.
                                            ServerMain.writeIntAndStringToClient(client, key, objectAttach);
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
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        finally {
            // Chiudo il 
            ServerMain.scheduledThreadPool.shutdown();
            System.out.println("Server terminato.");
        }
        
    }
}
