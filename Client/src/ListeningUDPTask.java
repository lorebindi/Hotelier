import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.channels.*;
import java.io.IOException;

public class ListeningUDPTask implements Runnable{
    
    private static final String MULTICAST_GROUP = ClientFileConfigurationReader.getClientMulticastAddress(); // Indirizzo di multicast.
    private static final int MULTICAST_PORT = Integer.parseInt(ClientFileConfigurationReader.getClientMulticastPort()); // Porta di multicast
    private final SocketChannel server; // SocketChannel aperto per la comunicazione client-server: appena questo viene chiuso il task deve terminare.

    private final int timeout = ClientFileConfigurationReader.getClientTimeout(); // timeout in millisecondi
    
    public ListeningUDPTask(SocketChannel server) {
        this.server = server;
    }

    public void run() {
        byte[] buffer = new byte[1024];
        try{
            MulticastSocket socket = new MulticastSocket(MULTICAST_PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.joinGroup(group);
            socket.setSoTimeout(timeout);
            // Finché il client è connesso è al server può ricevere messaggi UDP.
            while(server.isOpen()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // Questa chiamata lancia una SocketTimeoutException dopo il timeout
                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength());
                    ConsoleManage.synchronizedPrint("[Multicast UDP message received] >> " + msg);
                } catch (SocketTimeoutException e) {
                    /* Il timeout è scaduto ma non è stato ricevuto alcun pacchetto
                    Questo blocco viene utilizzato per controllare periodicamente che il server non sia stato chiuso.
                    Continua il ciclo */
                } catch (IOException e) {
                    ConsoleManage.synchronizedErrPrint("IO exception in ListeningUDPTask: " + e.getMessage());
                    break; // Esci dal ciclo in caso di eccezioni IO diverse dal timeout
                }
            }
            // Il canale è stato chiuso quindi il client non può più ricevere messaggi UDP.
            socket.leaveGroup(group);
            socket.close();
            ConsoleManage.synchronizedPrint("[Listening UDP Thread] Finished.\n");
        } catch (IOException ex) {
            ConsoleManage.synchronizedErrPrint("Error in ListeningUDPTask: " + ex.getMessage()+"\n");
        }
    }
    
}

