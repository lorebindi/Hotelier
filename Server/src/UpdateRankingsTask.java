import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.PriorityBlockingQueue;

/* 
Questa classe rappresenta il task lato server che si preoccupa di:
1) Ricalcolare tutti i ranking per tutte le città.
2) Inviare datagram UDP nel caso in cui il migliore hotel
   di una determinata città sia cambiato.
*/
public class UpdateRankingsTask implements Runnable {
    
    private static final String MULTICAST_GROUP = ServerFileConfigurationReader.getMulticastAddress(); // Indirizzo di multicast.
    private static final int MULTICAST_PORT = Integer.parseInt(ServerFileConfigurationReader.getMulticastPort()); // Porta di multicast

    /**
     * Metodo che ricalcola il ranking degli Hotel sulla base della media ponderata di ciascun Hotel.
     * @return Una HashMap contenente coppie composte dal nome della città e dall'Id dell'hotel migliore
     *          in quella città prima del ricalcolo.
     */
    private HashMap<String,Hotel> rankingRecalculation() {
        ConcurrentHashMap<String, RankingStructure> rankings = ServerMain.getRankings();
        // Mi creo la hashMap in cui vado a memorizzare temporaneamente tutti i primi hotel di ogni città, prima del ricalcolo del ranking
        HashMap<String, Hotel> oldBestHotel = new HashMap<>();
        // Per ogni citta:
        for(Map.Entry<String, RankingStructure> entry : rankings.entrySet()) {
            ConcurrentSkipListSet<Hotel> ranking = entry.getValue().getRanking();
            // Memorizzo l'Id del migliore hotel per quella città.
            oldBestHotel.put(entry.getValue().getCity(), ranking.first());
            // Svuoto il ranking
            ranking.clear();
            // Prendo tutti gli hotel di quella città.
            Collection<Hotel> hotelOfCity = ServerMain.getHotelsOfCity(entry.getKey()).values();
            // Ricalcolo la media pesata degli hotel di quella città.
            for(Hotel hotel : hotelOfCity)
                hotel.getWeightedAverageReviews();
            // Ricalcolo il ranking per quella città.
            for(Hotel hotel : hotelOfCity)
                ranking.add(hotel);
        }
        return oldBestHotel;
    }

    /**
     * Metodo che invia messaggi UDP per notificare i cambiamenti per i migliori Hotel di ogni città.
     * @param oldBestHotel HashMap contenente coppie composte dal nome della città e dall'Id dell'hotel migliore
     *                     in quella città prima del ricalcolo.
     */
    private void sendUdpMessagge( HashMap<String,Hotel> oldBestHotel) {

        ConcurrentHashMap<String, RankingStructure> rankings = ServerMain.getRankings();
        for(Map.Entry<String, RankingStructure> entry : rankings.entrySet()) {
            // Recupero un ranking di una determinata città.
            ConcurrentSkipListSet<Hotel> ranking = entry.getValue().getRanking();
            // Se il nuovo migliore hotel per quella città è diverso da quello vecchio allora invio messaggio UDP a tutti i cli.
            if(!ranking.first().equals(oldBestHotel.get(entry.getValue().getCity()))) {
                try (MulticastSocket socket = new MulticastSocket()) {
                    // Creo il multicast group
                    InetAddress group = InetAddress.getByName(UpdateRankingsTask.MULTICAST_GROUP);
                    byte[] data = ("New best Hotel for " + entry.getValue().getCity()+ ": '" + ranking.first().getName() + "'.").getBytes();
                    DatagramPacket packet = new DatagramPacket(data, data.length, group, MULTICAST_PORT);
                    socket.send(packet);
                } catch (IOException ex) {
                    System.err.println("IO error with UDP messagge");
                }  
            }
        }
    }
    
    public void run(){
        // Ricalcolo il ranking per ogni città e mi restituisce tutti gli ex-primi hotel di ogni città. 
        HashMap<String, Hotel> oldBestHotel = this.rankingRecalculation();
        System.out.println("Ranking ricalcolato.\n");
        // Invio messaggio UDP al gruppo multicast per ogni primo hotel di ogni città cambiato.
        this.sendUdpMessagge(oldBestHotel);   
    }
        
}

