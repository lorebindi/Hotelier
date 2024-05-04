import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/* E' la classe che si preoccupa di tenere il ranking locale di una determinata città. */
public class RankingStructure {
      
    private final String citta;
    private PriorityBlockingQueue<String> ranking; // Contiene gli id degli hotel.
    
    public RankingStructure (String citta) {
        this.citta = citta;
        this.ranking = new PriorityBlockingQueue<String>(10,new ComparatoreHotel());
        for(Map.Entry<String, Hotel> entry : ServerMain.getHotelsOfCity(this.citta).entrySet()) {
            ranking.add(entry.getValue().getId());
        }
    }
    
    public String getCity(){
        return this.citta;
    }

    public PriorityBlockingQueue<String> getRanking() {
        return ranking;
    }
    
    @Override
    public String toString(){
        String risp = "";
        for(String id : this.ranking) {
            risp = risp + ServerMain.getHotelFromId(id).toString() +"\n";
        }
    
        return risp;
    }
    
}
