
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

    // Invia i valori passati come argomenti al server.
    private static void writeToServer (SocketChannel server, int[] integers, String[] strings) throws IOException{
        
        // Calcola il numero di byte necessari per gli interi
        int nBytes = integers.length * Integer.BYTES;
        // Calcola il numero di byte necessari per le coppie (lunghezza stringa - stringa)
        if(strings.length != 0) {
            for (String str : strings) {
                nBytes += Integer.BYTES + str.getBytes().length;
            }
        }
        
        ConsoleManage.synchronizedPrint("Bytes da inviare: "+ nBytes + "\n");
        
        // Riempimento dell'outputBuffer
        ByteBuffer outputBuffer = ByteBuffer.allocate(nBytes);
        // Inserisco nel buffer il codice della registrazione
        outputBuffer.putInt(integers[0]);
        // Inserisco nel buffer le coppie (lunghezza stringhe - stringhe)
        for (String str : strings) {
            outputBuffer.putInt(str.length());
            outputBuffer.put(str.getBytes());
        }
        // Se l'operazione è la numero 6 devo aggiungere anche i valori della recensione.
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
            ConsoleManage.synchronizedPrint("Bytes effettivamente inviati: "+ bytesWritten+"\n");
        } catch (ClosedChannelException cce) {
            // Il canale è chiuso dal lato server.
            server.close();
            throw new ClosedChannelException();
        }
        catch (IOException e) {
            throw new IOException();
        }
         
    }
    
    // Restituisce un intero ricevuto dal server oppure -5 se il canale è stato chiuso dal server.
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
            ConsoleManage.synchronizedPrint("[readIntegerFromServer] Numero byte letti: " + nBytesRead+"\n");
            inputBuffer.flip();
            int integer = inputBuffer.getInt();
            ConsoleManage.synchronizedPrint("[readIntegerFromServer] Numero letto: " + integer+"\n");
            return integer;
        }
        catch (IOException e) {
            throw new IOException();
        }
    }
    
    // Restituisce un array di byte ricevuto dal server oppure un array di un singolo byte se il server ha chiuso il canale.
    private static byte[] readStringFromServer(SocketChannel server, int nByteToRead) throws IOException {
        try{
            ByteBuffer inputBuffer = ByteBuffer.allocate(nByteToRead);
            // Ricevo il codice dal server.
            int nBytesRead = server.read(inputBuffer);
            if(nBytesRead == -1 || !server.isOpen()) {
                server.close(); // Chiudo il canale.
                return new byte[1];
            }
            ConsoleManage.synchronizedPrint("Bytes ricevuti: " + nBytesRead+"\n");
            // Controllo di aver ricevuto tutti i bytes che mi aspetto, altrimenti continuo a leggere.
            while (inputBuffer.hasRemaining() ) {
                nBytesRead += server.read(inputBuffer);
                if(nBytesRead == -1 || !server.isOpen()) {
                    server.close(); // Chiudo il canale.
                    return new byte[1];
                }
            }
            ConsoleManage.synchronizedPrint("[readStringFromServer] Bytes ricevuti: " + nBytesRead+"\n");
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
    
    // Restituisce i valori numerici di una recensione letti da riga di comando.
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

    // Restituisce l'intero letto da riga di comando
    private static int readOperation(Scanner scanner){
        int operation = 0;
        boolean isValidInput = false; // Flag per uscire dal ciclo.

        if(ClientMain.username.isEmpty()) {
            HashMap<Integer, Integer> map = new HashMap<>(3); // Mappa utilizzata esclusivamente per l'output su console.
            map.put(3,4);
            map.put(4,5);
            map.put(5,8);
            String prompt = ("Choose: \n 1. Registration \n 2. Login \n 3. Search for a hotel in a city \n "
                    + "4. Search all hotel in a city \n 5. Exit. \n");
            while (!isValidInput) {
                operation = ConsoleManage.synchronizedIntegerRead(prompt, scanner);
                if (operation >= 1 && operation <= 5) {
                    isValidInput = true; // Imposta la flag su true se il numero è valido e nell'intervallo 1-7
                } else {
                    prompt = ("Choice not allowed. Please enter a number between 1 and 5.\n");
                }
            }
            if(operation == 3 || operation == 4 || operation == 5)
                operation = map.get(operation);
        }
        else {
            String prompt = ("Choose: \n 1. Registration \n 2. Login \n 3. Logout \n 4. Search for a hotel in a city \n "
                + "5. Search all hotel in a city \n 6. Insert review \n 7. Show my badges.\n");
            while (!isValidInput) {
                operation = ConsoleManage.synchronizedIntegerRead(prompt, scanner);
                if (operation >= 1 && operation<=8) {
                    isValidInput = true; // Imposta la flag su true se il numero è valido e nell'intervallo 1-7
                } else {
                    prompt = ("Choice not allowed. Please enter a number between 1 and 7.\n");
                }
            }
        }
        return operation;
    }
    
    // Legge da riga di comando username e password, invia tutto al server, stampa risultato dell'operazione di registrazione.
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

    // Legge da riga di comando username e password, invia tutto al server, stampa risultato dell'operazione di login.
    private static void login (SocketChannel server,int operation, Scanner scanner) throws IOException {
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Lettura da riga di comando dell'username.
        String username = ClientMain.readString(scanner, "username");
        ClientMain.username = username;
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
    
    // Legge da riga di comando username, invia tutto al server, stampa risultato dell'operazione di logout.
    private static void logout (SocketChannel server,int operation, Scanner scanner) throws IOException{
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Invio al server: l'operazione che voglio eseguire e l'username.
        ClientMain.writeToServer(server, new int[]{operation}, new String[]{ClientMain.username});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        ConsoleManage.synchronizedPrint("[logout] codice ricevuto: "+ code);
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
    
    // Legge da riga di comando nomeHotel e citta, invia tutto al server, stampa risultato dell'operazione di searchHotel.
    private static void searchHotel (SocketChannel server,int operation, Scanner scanner) throws IOException{
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Lettura da riga di comando del nome dell'hotel e della città.
        String nomeHotel = ClientMain.readString(scanner, "name hotel");
        String citta = ClientMain.readString(scanner, "city");
        // Invio al server: l'operazione che voglio eseguire e l'username.
        ClientMain.writeToServer(server, new int[]{operation}, new String[]{nomeHotel, citta});
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
    
    // Legge da riga di comando la citta, invia tutto al server, stampa risultato dell'operazione di searchHotels.
    private static void searchHotels (SocketChannel server,int operation, Scanner scanner) throws IOException {
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        // Lettura da riga di comando della città.
        String citta = ClientMain.readString(scanner, "city");
        // Invio al server: l'operazione che voglio eseguire e la citta.
        ClientMain.writeToServer(server, new int[] {operation}, new String[] {citta});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        // Stampo il risultato
        switch(code){
            case 0: ConsoleManage.synchronizedPrint("Search hotels successfully.\n----------------------------------------------\n");
                    // Ricevo dal server la lunghezza della stringa che rappresenta gli hotel per quella città.
                    int stringHotels_length = ClientMain.readIntegerFromServer(server);
                    ConsoleManage.synchronizedPrint("[searchHotel] Lunghezza stringa ricevuta: " + stringHotels_length);
                    // Ricevo dal server la sequenza di byte che corrisponde alla stringa che rappresenta l'insieme di hotel.
                    byte[] byteStringHotel = ClientMain.readStringFromServer(server, stringHotels_length);
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
    
    // Legge da riga di comando nomeHotel, città e la recensione, invia tutto al server, stampa risultato dell'operazione di insertReview.
    private static void insertReview (SocketChannel server,int operation, Scanner scanner) throws IOException {
        ConsoleManage.synchronizedPrint("----------------------------------------------\n");
        if(ClientMain.username.isEmpty()) {
            ConsoleManage.synchronizedPrint("To review a hotel you must be logged in.\n----------------------------------------------\n");
            return;
        }
        // Ricevo da riga di comando il nome dell'hotel.
        String nomeHotel = ClientMain.readString(scanner, "hotel name");
        // Ricevo da riga di comando la città dell'hotel.
        String citta = ClientMain.readString(scanner, "city");
        // Ricevo da riga di comando i voti della recensione.
        int[] scores = ClientMain.readScoresReview(scanner);
        // Invio al server: l'operazione che voglio eseguire, il nome dell'hotel, la citta, i voti.
        int[] integers = new int[6];
        integers[0] = operation;
        System.arraycopy(scores, 0, integers, 1, scores.length);
        ClientMain.writeToServer(server, integers, new String[] {nomeHotel, citta});
        // Ricevo dal server l'output dell'operazione richiesta.
        int code = ClientMain.readIntegerFromServer(server);
        // Stampo il risultato.
        ConsoleManage.synchronizedPrint("[Insert Review] codice ricevuto: "+code+"\n");
        switch(code){
            case 0: ConsoleManage.synchronizedPrint("Review correctly added.\n----------------------------------------------\n");
                    break;
            case -1: ConsoleManage.synchronizedPrint("To review a hotel you must be logged in.\n----------------------------------------------\n");
                    break;
            case -2: ConsoleManage.synchronizedPrint("Server error.\n----------------------------------------------\n");
                    break;
            case -5: ConsoleManage.synchronizedPrint("Connection interrupted by the server.\n----------------------------------------------\n");
                     break;
        }
        
    }
    
    // Invia al server l'username memorizzato, stampa il risultato dell'operazione showMyBadge.
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
        ConsoleManage.synchronizedPrint("[Insert Review] codice ricevuto: "+code);
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

    // Comunica al server la chiusura del canale e chiude il canale.
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

                ConsoleManage.synchronizedPrint("Hai chiesto l'operazione: " + operation + "\n");
                
                switch(operation) {
                
                    case 1: // Registrazione
                            ClientMain.registration(server, operation, scanner);
                            break;
                        
                    case 2: // Login
                            ClientMain.login(server, operation, scanner);
                            break;
                        
                    case 3: // Logout
                            ClientMain.logout(server, operation, scanner);
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
        
