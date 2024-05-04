import java.util.Comparator;

public class ComparatoreHotel implements Comparator<String>{

    public int compare (String id1, String id2) {
        Hotel a = ServerMain.getHotelFromId(id1);
        Hotel b = ServerMain.getHotelFromId(id2);
        if (a.getCity().equals("Aosta")) {
            int c = 0;
        }
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


