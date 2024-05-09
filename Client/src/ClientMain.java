import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.util.HashMap;
import java.util.Scanner;
import java.io.IOException;

public class ClientMain {

    public static final String Ip = ClientFileConfigurationReader.getClientIp();
    public static final int DEFAULT_PORT = ClientFileConfigurationReader.getClientPort();
    public static String username = "";

    /**
     * Metodo che scrive un insieme di interi e stringhe al server tramite SocketChannel.
     * @param server Il SocketChannel attraverso il quale i dati saranno inviati al server.
     * @param integers Un array di interi che include i dati da inviare. Il primo intero è sempre
     *                 il codice dell'operazione da eseguire sul server.
     * @param strings Un array di stringhe i cui dati vengono inviati al server.
     * @throws IOException
     */
    private static void writeToServer (SocketChannel server, int[] integers, String[] strings) throws IOException{
        
        // Calcola il numero di byte necessari per gli interi
        int nBytes = integers.length * Integer.BYTES;
        // Calcola il numero di byte necessari per le coppie (lunghezza stringa - stringa)
        if(strings.length != 0) {
            for (String str : strings) {
                nBytes += Integer.BYTES + str.getBytes().length;
            }
        }
        
        // Riempimento dell'outputBuffer
        ByteBuffer outputBuffer = ByteBuffer.allocate(nBytes);
        // Inserisco nel buffer il codice dell'operazione.
        outputBuffer.putInt(integers[0]);
        // Inserisco nel buffer le coppie (lunghezza stringhe - stringhe)
        for (String str : strings) {
            outputBuffer.putInt(str.length());
            outputBuffer.put(str.getBytes());
        }
        // Se l'operazione è la numero 6 (insertReview) devo aggiungere anche i valori della recensione.
        if(integers[0] == 6) { 
            for(int i = 1; i<integers.length; i++)
                outputBuffer.putInt(integers[i]);
        }
        
        int bytesWritten = 0;
        
        try {
            // Invio al server tutto il contenuto del buffer
            outputBuffer.flip();
            while (outputBuffer.hasRemaining()) {
                bytesWritten = server.write(outputBuffer);
                if (bytesWritten == -1) 
                    break;
            }
        } catch (ClosedChannelException cce) {
            // Il canale è chiuso dal lato server.
            server.close();
            throw new ClosedChannelException();
        }
        catch (IOException e) {
            throw new IOException();
        }
         
    }

    /**
     * Metodo che legge un singolo intero dal server tramite SocketChannel.
     * @param server Il SocketChannel attraverso il quale i dati saranno inviati al server.
     * @return
     * @throws IOException
     */
    private static int readIntegerFromServer(SocketChannel server) throws IOException {
        try{
            ByteBuffer inputBuffer = ByteBuffer.allocate(Integer.BYTES);
            // Ricevo il codice dal server.
            int nBytesRead = server.read(inputBuffer);
            if(nBytesRead == -1 || !server.isOpen()) {
                server.close(); // Chiudo il canale.
                return -5;
            }
            // Controllo di aver ricevuto tutti i bytes che mi aspetto, altrimenti continuo a leggere.
            while (inputBuffer.hasRemaining()) {
                nBytesRead += server.read(inputBuffer);
                if(nBytesRead == -1 || !server.isOpen()) {
                    server.close(); // Chiudo il canale.
                    return -5;
                }
            }
            inputBuffer.flip();
            int integer = inputBuffer.getInt();
            return integer;
        }
        catch (IOException e) {
            throw new IOException();
        }
    }

    /**
     * Metodo che legge una stringa sotto forma di array di byte dal server tramite SocketChannel
     * assumendo che i primi 4 byte siano sempre la lunghezza della stringa.
     *
     * @param server Il SocketChannel attraverso il quale i dati saranno inviati al server.
     * @param nByteToRead Il numero dei bytes effettivi da leggere
     * @return byte[] Un array di byte contenente la stringa letta, oppure un array di byte
     *                di lunghezza 1 in caso di errore.
     * @throws IOException
     */
    private static byte[] readStringFromServer(SocketChannel server, int nByteToRead) throws IOException {
        try{
            ByteBuffer inputBuffer = ByteBuffer.allocate(nByteToRead);
            // Ricevo il codice dal server.
            int nBytesRead = server.read(inputBuffer);
            if(nBytesRead == -1 || !server.isOpen()) {
                server.close(); // Chiudo il canale.
                return new byte[1];
            }
            // Controllo di aver ricevuto tutti i bytes che mi aspetto, altrimenti continuo a leggere.
            while (inputBuffer.hasRemaining() ) {
                nBytesRead += server.read(inputBuffer);
                if(nBytesRead == -1 || !server.isOpen()) {
                    server.close(); // Chiudo il canale.
                    return new byte[1];
                }
            }
            // Prendo i byte dal ByteBuffer
            inputBuffer.flip();
            byte[] bytesReceived = new byte[nByteToRead];
            inputBuffer.get(bytesReceived);
            return bytesReceived;
        }
        catch (IOException e) {
            throw new IOException();
        }
    }

    /**
     * Metodo che legge da riga di comando i punteggi della recensione
     * di un hotel tramite Scanner. Se l'utente inserisce un valore non valido
     * il sistema lo obbliga a reinserirlo fino a quando non sarà corretto.
     *
     * @param scanner Lo Scanner utilizzato per leggere l'input dell'utente.
     * @return int[] Un array di interi contenenti i cinque punteggi di recensione validi.
     */
    private static int[] readScoresReview(Scanner scanner) {
        int[] scores = new int[5];
        final String globalScore = "Global score";
        final String positionScore = "Position score";
        final String cleaningScore = "Cleaning score";
        final String serviceScore = "Service score";
        final String priceScore = "Price score";
        String typeScore = "";
        for(int i=0; i<5; i++) {
            if(i == 0) typeScore = globalScore; 
            if(i == 1) typeScore = positionScore;
            if(i == 2) typeScore = cleaningScore;
            if(i == 3) typeScore = serviceScore;
            if(i == 4) typeScore = priceScore;
            String prompt = typeScore + " (from 0 to 5): ";
            // Controlli sul valore inserito.
            do {
                int inputScore = ConsoleManage.synchronizedIntegerRead(prompt, scanner);
                // Controllo se l'intero è compreso tra 0 e 5
                if (inputScore >= 0 && inputScore <= 5) {
                    scores[i] = inputScore;
                    break; // Esce dal ciclo se l'input è valido
                } else {
                    prompt = (typeScore + " is bigger than 5 or is smaller than 0. Please re-enter.\n");
                }
            } while (true);
        }
        return scores;
    }
    
    // Restituisce la stringa corretta letta da riga di comando. 
    private static String readString(Scanner scanner, String stringToShow){
        return ConsoleManage.synchronizedStringRead(stringToShow, scanner);
    }

    /**
     * Metodo che legge da riga di comando l'operazione richiesta dall'utente.
     * Le operazioni disponibili variano a seconda che l'utente sia loggato o meno.
     *
     * @param scanner Lo Scanner utilizzato per leggere l'input dell'utente.
     * @return int L'intero valido che rappresenta l'operazione richiesta.
     */
    private static int readOperation(Scanner scanner){
        int operation = 0;
        boolean isValidInput = false; // Flag per uscire dal ciclo.

        if(ClientMain.username.isEmpty()) {
            HashMap<Integer, Integer> map = new HashMap<>(3); // Mappa utilizzata esclusivamente per l'output su console.
            map.put(3,4);
            map.put(4,5);
            map.put(5,8);
            String prompt = ("Choose: \n 1. Registration \n 2. Login \n 3. Search for a hotel in a city \n "
                    + "4. Search all hotels in a city \n 5. Exit. \n");
            while (!isValidInput) {
                operation = ConsoleManage.synchronizedIntegerRead(prompt, scanner);
                if (operation >= 1 && operation <= 5) {
                    isValidInput = true; // Imposta la flag su true se il numero è valido e nell'intervallo 1-7
                } else {
                    prompt = ("Choice not allowed. Please enter a number between 1 and 5.\n");
                }
            }
            ConsoleManage.synchronizedPrint("Hai chiesto l'operazione: " + operation + "\n");
            if(operation == 3 || operation == 4 || operation == 5)
                operation = map.get(operation);
        }
        else {
            String prompt = ("Choose: \n 1. Logout \n 2. Search for a hotel in a city \n "
                + "3. Search all hotels in a city \n 4. Insert review \n 5. Show my badges.\n");
            while (!isValidInput) {
                operation = ConsoleManage.synchronizedIntegerRead(prompt, scanner);
                if (operation >= 1 && operation<=5) {
                    isValidInput = true; // Imposta la flag su true se il numero è valido e nell'intervallo 1-7
                } else {
                    prompt = ("Choice not allowed. Please enter a number between 1 and 7.\n");
                }
                ConsoleManage.synchronizedPrint("You asked for the operation: " + operation + "\n");
                operation+=2;
            }
        }
        return operation;
    }

    /**
     *  Metodo che gestisce la registrazione di un nuovo utente. Prima recupera
     *  username e password da riga di comando, dopo di che li invia al server tramite
     *  SocketChannel, infine attende l'esito dell'operazione e la stampa.
     *
     * @param server Il SocketChannel per comunicare con il server.
     * @param operation Il codice dell'operazione registrazione.
     * @param scanner Lo Scanner utilizzato per leggere l'input dell'utente.
     * @throws IOException
     */
    private static void registration (SocketChannel server,int operation, Scanner scanner) throws IOException {
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Ricevo username e password da riga di comando
        String username = ClientMain.readString(scanner, "username");
        String password = ClientMain.readString(scanner, "password");
        // Invio al server: l'operazione che voglio eseguire, username e password.
        ClientMain.writeToServer(server, new int[]{operation}, new String[]{username, password});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        // Stampo il risultato
        switch (code) {
            case 0: ConsoleManage.synchronizedPrint("Registration completed.\n----------------------------------------------\n");
                    break;
            case 1: ConsoleManage.synchronizedPrint("Username already used. \n----------------------------------------------\n");
                    break;
            case -1: ConsoleManage.synchronizedPrint("Server error.\n----------------------------------------------\n");
                     break;
            case -5: ConsoleManage.synchronizedPrint("Connection interrupted by the server.\n----------------------------------------------\n");
                     break;
        }
    }

    /**
     * Metodo che gestisce il login di un nuovo utente. Prima recupera username
     * e password, dopo di che li invia al server tramite SocketChannel insieme al il codice
     * dell'operazione, infine attende l'esito dell'operazione e la stampa.
     *
     * @param server Il SocketChannel per comunicare con il server.
     * @param operation Il codice del login.
     * @param scanner Lo Scanner utilizzato per leggere l'input dell'utente.
     * @throws IOException
     */
    private static void login (SocketChannel server,int operation, Scanner scanner) throws IOException {
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Lettura da riga di comando dell'username.
        String username = ClientMain.readString(scanner, "username");
        // Lettura da riga di comando della password.
        String password = ClientMain.readString(scanner, "password");
        // Invio al server: l'operazione che voglio eseguire, username e password.
        ClientMain.writeToServer(server, new int[]{operation}, new String[]{username, password});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        // Stampo il risultato.
        switch(code){
            case 0: ConsoleManage.synchronizedPrint("Login successfully.\n----------------------------------------------\n");
                    ClientMain.username = username;
                    break;
            case -1:ConsoleManage.synchronizedPrint("User already logged in.\n----------------------------------------------\n");
                    break;
            case -2:ConsoleManage.synchronizedPrint("Non-existent username.\n----------------------------------------------\n");
                    break;
            case -3:ConsoleManage.synchronizedPrint("Invalid password.\n----------------------------------------------\n");
                    break;
            case -4:ConsoleManage.synchronizedPrint("Server error.\n----------------------------------------------\n");
                    break;
            case -5: ConsoleManage.synchronizedPrint("Connection interrupted by the server.\n----------------------------------------------\n");
                     break;
        }
        
    }

    /**
     * Metodo che gestisce il logout di un utente. Invia al server tramite SocketChannel il
     * codice dell'operazione, l'username dell'utente, attende l'esito dell'operazione e la stampa.
     * @param server Il SocketChannel per comunicare con il server.
     * @param operation Il codice dell'logout.
     * @throws IOException
     */
    private static void logout (SocketChannel server,int operation) throws IOException{
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Invio al server: l'operazione che voglio eseguire e l'username.
        ClientMain.writeToServer(server, new int[]{operation}, new String[]{ClientMain.username});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        // Stampo il risultato.
        switch(code){
            case 0: ConsoleManage.synchronizedPrint("Logout successfully.\n----------------------------------------------\n");
                    ClientMain.username = "";
                    break;
            case -1:ConsoleManage.synchronizedPrint("Unable to logout because no login was made.\n----------------------------------------------\n");
                    break;
            case -2:ConsoleManage.synchronizedPrint("Username provided is different from login username.\n----------------------------------------------\n");
                    break;
            case -3:ConsoleManage.synchronizedPrint("Server error.\n----------------------------------------------\n");
                    break;
            case -5: ConsoleManage.synchronizedPrint("Connection interrupted by the server.\n----------------------------------------------\n");
                     break;
        }
    }

    /**
     * Metodo che gestisce la richiesta di informazioni da parte di un utente di uno specifico hotel.
     * Invia al server tramite SocketChannel il codice dell'operazione, il nome,
     * la città dell'hotel, attende l'esito dell'operazione e la stampa.
     * @param server Il SocketChannel per comunicare con il server.
     * @param operation Il codice della registrazione.
     * @param scanner Lo Scanner utilizzato per leggere l'input dell'utente.
     * @throws IOException
     */
    private static void searchHotel (SocketChannel server,int operation, Scanner scanner) throws IOException{
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Lettura da riga di comando del nome dell'hotel e della città.
        String hotelName = ClientMain.readString(scanner, "name hotel");
        String city = ClientMain.readString(scanner, "city");
        // Invio al server: l'operazione che voglio eseguire e l'username.
        ClientMain.writeToServer(server, new int[]{operation}, new String[]{hotelName, city});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        switch(code){
            case 0: ConsoleManage.synchronizedPrint("Search hotel successfully.\n----------------------------------------------\n");
                    // Ricevo dal server la lunghezza della stringa che rappresenta l'hotel.
                    int stringHotel_length = ClientMain.readIntegerFromServer(server);
                    // Ricevo dal server la sequenza di byte che corrisponde alla stringa che rappresenta l'hotel.
                    byte[] byteStringHotel = ClientMain.readStringFromServer(server, stringHotel_length);
                    ConsoleManage.synchronizedPrint(new String(byteStringHotel) + ".\n----------------------------------------------\n");
                    break;
            case -1:ConsoleManage.synchronizedPrint("Non-existent hotel.\n----------------------------------------------\n");
                    break;
            case -2:ConsoleManage.synchronizedPrint("Server error.\n----------------------------------------------\n");
                    break;
            case -5: ConsoleManage.synchronizedPrint("Connection interrupted by the server.\n----------------------------------------------\n");
                     break;
        }
        
    }

    /**
     * Metodo che gestisce la richiesta di informazioni da parte di un utente della classifica di hotel di
     * una determinata città. Invia al server tramite SocketChannel il codice dell'operazione, il nome e
     * la città dell'hotel, attende l'esito dell'operazione e la stampa.
     * @param server Il SocketChannel per comunicare con il server.
     * @param operation Il codice della searchHotels.
     * @param scanner Lo Scanner utilizzato per leggere l'input dell'utente.
     * @throws IOException
     */
    private static void searchHotels (SocketChannel server,int operation, Scanner scanner) throws IOException {
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Lettura da riga di comando della città.
        String city = ClientMain.readString(scanner, "city");
        // Invio al server: l'operazione che voglio eseguire e la citta.
        ClientMain.writeToServer(server, new int[] {operation}, new String[] {city});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        // Stampo il risultato
        switch(code){
            case 0: ConsoleManage.synchronizedPrint("Search hotels successfully.\n----------------------------------------------\n");
                    // Ricevo dal server la lunghezza della stringa che rappresenta gli hotel per quella città.
                    int stringHotels_length = ClientMain.readIntegerFromServer(server);
                    // Ricevo dal server la sequenza di byte che corrisponde alla stringa che rappresenta l'insieme di hotel.
                    byte[] byteStringHotel = ClientMain.readStringFromServer(server, stringHotels_length);
                    String temp = new String(byteStringHotel);
                    ConsoleManage.synchronizedPrint(new String(byteStringHotel) + ".\n----------------------------------------------\n");
                    break;
            case -1:ConsoleManage.synchronizedPrint("Non-existent hotel in that city.\n----------------------------------------------\n");
                    break;
            case -2:ConsoleManage.synchronizedPrint("Server error.\n----------------------------------------------\n");
                    break;
            case -5: ConsoleManage.synchronizedPrint("Connection interrupted by the server.\n----------------------------------------------\n");
                     break;
        }
    }

    /**
     * Metodo che gestisce l'inserimento di una recensione per un determinato hotel da parte dell'utente .
     * Invia al server tramite SocketChannel il codice dell'operazione, il nome, la città dell'hotel, e i punteggi, dopo di che
     * attende l'esito dell'operazione e la stampa.
     * @param server Il SocketChannel per comunicare con il server.
     * @param operation Il codice della insertReview.
     * @param scanner Lo Scanner utilizzato per leggere l'input dell'utente.
     * @throws IOException
     */
    private static void insertReview (SocketChannel server,int operation, Scanner scanner) throws IOException {
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        if(ClientMain.username.isEmpty()) {
            ConsoleManage.synchronizedPrint("To review a hotel you must be logged in.\n----------------------------------------------\n");
            return;
        }
        // Ricevo da riga di comando il nome dell'hotel.
        String hotelName = ClientMain.readString(scanner, "hotel name");
        // Ricevo da riga di comando la città dell'hotel.
        String city = ClientMain.readString(scanner, "city");
        // Ricevo da riga di comando i voti della recensione.
        int[] scores = ClientMain.readScoresReview(scanner);
        // Invio al server: l'operazione che voglio eseguire, il nome dell'hotel, la citta, i voti.
        int[] integers = new int[6];
        integers[0] = operation;
        System.arraycopy(scores, 0, integers, 1, scores.length);
        ClientMain.writeToServer(server, integers, new String[] {hotelName, city});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        // Stampo il risultato.
        switch(code){
            case 0: ConsoleManage.synchronizedPrint("Review correctly added.\n----------------------------------------------\n");
                    break;
            case -1: ConsoleManage.synchronizedPrint("To review a hotel you must be logged in.\n----------------------------------------------\n");
                    break;
            case -2: ConsoleManage.synchronizedPrint("Server error.\n----------------------------------------------\n");
                    break;
            case -3: ConsoleManage.synchronizedPrint("Inexistent hotel.\n----------------------------------------------\n");
                break;
            case -5: ConsoleManage.synchronizedPrint("Connection interrupted by the server.\n----------------------------------------------\n");
                     break;
        }
        
    }

    /**
     * Metodo che gestisce la richiesta di visualizzazione del badge da parte di un utente.
     * Il metodo invia al server tramite SocketChannel il codice dell'operazione e l'username, dopo di che
     * attende l'esito dell'operazione e la stampa.
     * @param server Il SocketChannel per comunicare con il server.
     * @param operation Il codice della showBadge.
     * @throws IOException
     */
    private static void showMyBadge (SocketChannel server,int operation) throws IOException {
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Invio al server: l'operazione che voglio eseguire e l'username.
        if(ClientMain.username.isEmpty()) {
            ConsoleManage.synchronizedPrint("To receive a badge you must be logged in.\n----------------------------------------------\n");
            return;
        }
        ClientMain.writeToServer(server, new int[] {operation}, new String[] {ClientMain.username});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        // Stampo il risultato.
        switch(code){
            case 0: ConsoleManage.synchronizedPrint("Badge correctly received.\n----------------------------------------------\n");
                    // Ricevo dal server la lunghezza della stringa che rappresenta il badge.
                    int stringBadge_length = ClientMain.readIntegerFromServer(server);
                    // Ricevo dal server la sequenza di byte che corrisponde alla stringa che rappresenta il badge.
                    byte[] byteStringBadge = ClientMain.readStringFromServer(server, stringBadge_length);
                    ConsoleManage.synchronizedPrint("Yours badge: " + new String(byteStringBadge) + ".\n----------------------------------------------\n");
                    break;
            case -1: ConsoleManage.synchronizedPrint("To receive a badge you must be logged in.\n----------------------------------------------\n");
                    break;
            case -2: ConsoleManage.synchronizedPrint("Server error.\n----------------------------------------------\n");
                    break;
            case -5: ConsoleManage.synchronizedPrint("Connection interrupted by the server.\n----------------------------------------------\n");
                     break;
        }
    }

    /**
     * Metodo che chiude la connessione con il server. Il metodo invia il server
     * il codice dell'operazione di chiusura della connessione e poi chiude il canale.
     * @param server Il SocketChannel per comunicare con il server.
     * @param operation Il codice della closeConnection.
     * @throws IOException
     */
    private static void closeConnection(SocketChannel server, int operation) throws IOException{
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Invio al server: l'operazione che voglio eseguire e l'username.
        if(!ClientMain.username.isEmpty()) {
            ConsoleManage.synchronizedPrint("To close the connection you must first log out.\n----------------------------------------------\n");
            return;
        }
        ClientMain.writeToServer(server, new int[] {operation}, new String[] {});
        server.close(); // Chiudo il canale
        ConsoleManage.synchronizedPrint("Connection closed correctly.\n----------------------------------------------\n");
    }
    
    public static void main(String[] args) {  

        try { 
            SocketAddress address = new InetSocketAddress(InetAddress.getByName(ClientMain.Ip),ClientMain.DEFAULT_PORT);
            SocketChannel server = SocketChannel.open(address);
            // Faccio partire il thread in ascolto per messaggi UDP.
            Thread listeningUDPThread = new Thread(new ListeningUDPTask(server));
            listeningUDPThread.start();
            // Finché il canale con il server è aperto.
            while (server.isOpen()) {
                // Inizializzazione Scanner
                Scanner scanner = new Scanner(System.in);
                // Cattura dell'operazione richiesta dall'utente.
                int operation = ClientMain.readOperation(scanner); // Operazione richiesta dal utente.
                
                switch(operation) {
                
                    case 1: // Registrazione
                            ClientMain.registration(server, operation, scanner);
                            break;
                        
                    case 2: // Login
                            ClientMain.login(server, operation, scanner);
                            break;
                        
                    case 3: // Logout
                            ClientMain.logout(server, operation);
                            break;
                            
                    case 4: // searchHotel
                            ClientMain.searchHotel(server, operation, scanner);
                            break;
                            
                    case 5: // searchHotels
                            ClientMain.searchHotels(server, operation, scanner);
                            break;
                    
                    case 6: // insertReview
                            ClientMain.insertReview(server, operation, scanner);
                            break;
                            
                    case 7: // showMyBadge
                            ClientMain.showMyBadge(server, operation);
                            break;

                    case 8: // closeConnection
                            ClientMain.closeConnection(server,operation);
                            break;
                }
            }
            // Attesa terminazione del thread UDP
            try {
                listeningUDPThread.join();
            } catch (InterruptedException e) {
                ConsoleManage.synchronizedErrPrint("Error while waiting for the listening thread to finish.\n");
            }
        }
        catch (IOException ex) {
            ConsoleManage.synchronizedErrPrint(ex.toString());
        }
    
    }
}
        
