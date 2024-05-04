
import java.time.Instant;

public class Review {
    private static int id = 0;
    private final int votoComplessivo;
    private final int posizione;
    private final int pulizia; 
    private final int servizio;
    private final int prezzo;
    private final long timestamp; // Secondi passati dal 1 gennaio 1970.
    
    public Review(int votoComlessivo, int posizione, int pulizia, int servizio, int prezzo) {
        Review.id ++;
        this.votoComplessivo = votoComlessivo;
        this.posizione = posizione;
        this.pulizia = pulizia;
        this.servizio = servizio;
        this.prezzo = prezzo;
        this.timestamp = Instant.now().getEpochSecond();
    }

    public static int getLastId() {
        return id;
    }
    
    public int getId() {
        return this.id;
    }
    
    public int getVotoComplessivo() {
        return this.votoComplessivo;
    }
  
    public int getPosizione() {
        return this.posizione;
    }

    public int getPulizia() {
        return this.pulizia;
    }

    public int getServizio() {
        return this.servizio;
    }

    public int getPrezzo() {
        return this.prezzo;
    }

    public long getTimestamp() {
        return this.timestamp;
    }


    /*Per il calcolo del peso della singola recensione
    si ricorre ad una semplice funzione di decadimento y=e^((-1/300)*x).*/
    public double getWeightReview() {
        long currentTimestamp = Instant.now().getEpochSecond();
        long differenceInDays = (currentTimestamp - this.getTimestamp()) / (24 * 60 * 60);
        return Math.exp(-((double) 1 /300)*differenceInDays); // y=e^((-1/300)*x)
    }
    
    public String toString() {
        return "Global score: " + this.votoComplessivo + ", Position score: " + this.posizione + ", Cleaning score: " 
                + this.pulizia + ", Service score: " + this.servizio + ", Price score: " + this.prezzo;
    }
    
}
