import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.PriorityBlockingQueue;

/* E' la classe che si preoccupa di tenere il ranking locale di una determinata città. */
public class RankingStructure {
      
    private final String citta;
    private ConcurrentSkipListSet<Hotel> ranking; // Contiene gli id degli hotel.
    
    public RankingStructure (String citta) {
        this.citta = citta;
        this.ranking = new ConcurrentSkipListSet<Hotel>(new ComparatoreHotel());
        for(Map.Entry<String, Hotel> entry : ServerMain.getHotelsOfCity(this.citta).entrySet()) {
            ranking.add(entry.getValue());
        }
    }
    
    public String getCity(){
        return this.citta;
    }

    public ConcurrentSkipListSet<Hotel> getRanking() {
        return ranking;
    }
    
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for(Hotel hotel : this.ranking){
            sb.append(i).append(") ").append(hotel.toString()).append("\n");
            i++;
        }
        return sb.toString();
    }
    
}
