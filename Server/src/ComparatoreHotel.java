import java.util.Comparator;

public class ComparatoreHotel implements Comparator<Hotel>{

    public int compare (Hotel a, Hotel b) {
        /*Hotel a = ServerMain.getHotelFromId(h1);
        Hotel b = ServerMain.getHotelFromId(id2);*/
        // Calcolo media ponderata su tutte le recensioni dei due hotel in base alla recenza.
        double a_weightedAverage = a.getWeightedAverageReviews();
        double b_weightedAverage = b.getWeightedAverageReviews();
        int risp = 0;
        // Tra i due hotel chi ha la media ponderata più alta viene per primo
        if (a_weightedAverage != b_weightedAverage) {
            risp = Double.compare(b_weightedAverage, a_weightedAverage);
            return risp;
        }
        // A parità di media ponderata viene prima chi ha più recensioni
        if (a.get_nReview() != b.get_nReview()){
            risp = Integer.compare(b.get_nReview(), a.get_nReview());
            return risp;
        }
        else {
            // A parità di tutto eseguo un ordinamento lessicografico.
            risp = a.getId().compareTo(b.getId());
            return risp;
        }
    }
}


