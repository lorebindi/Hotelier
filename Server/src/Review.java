
import java.time.Instant;

public class Review {
    private static int id = 0;
    private final int overallVote;
    private final int positionVote;
    private final int cleanlinessVote;
    private final int serviceVote;
    private final int priceVote;
    private final long timestamp; // Secondi passati dal 1 gennaio 1970.
    
    public Review(int votoComlessivo, int posizione, int pulizia, int servizio, int prezzo) {
        Review.id ++;
        this.overallVote = votoComlessivo;
        this.positionVote = posizione;
        this.cleanlinessVote = pulizia;
        this.serviceVote = servizio;
        this.priceVote = prezzo;
        this.timestamp = Instant.now().getEpochSecond();
    }

    public int getId() {
        return this.id;
    }
    
    public int getVotoComplessivo() {
        return this.overallVote;
    }
  
    public int getPositionVote() {
        return this.positionVote;
    }

    public int getCleanlinessVote() {
        return this.cleanlinessVote;
    }

    public int getServiceVote() {
        return this.serviceVote;
    }

    public int getPriceVote() {
        return this.priceVote;
    }

    public long getTimestamp() {
        return this.timestamp;
    }


    /**
     * Metodo che calcola il peso della recensione basandosi sulla sua attualità.
     * In particolare il peso viene determinato da una funzione esponenziale decrescente,
     * dove il tempo trascorso dal momento della pubblicazione delle recensione influisce
     * negativamente sul suo peso.
     * @return double Il peso della recensione compreso tra 0 e 1.
     */
    public double getWeightReview() {
        long currentTimestamp = Instant.now().getEpochSecond();
        long differenceInDays = (currentTimestamp - this.getTimestamp()) / (24 * 60 * 60);
        return Math.exp(-((double) 1 /300)*differenceInDays); // y=e^((-1/300)*x)
    }
    
    public String toString() {
        return "Global score: " + this.overallVote + ", Position score: " + this.positionVote + ", Cleaning score: "
                + this.cleanlinessVote + ", Service score: " + this.serviceVote + ", Price score: " + this.priceVote;
    }
    
}
