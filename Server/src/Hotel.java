

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Hotel {
    private final String id;
    private final String name;
    private final String description;
    private final String city;
    private String phone;
    private ArrayList<String> services;
    private ConcurrentHashMap<String, Review> ratings;

    private Double weightedAverageReviews = null; // Campo aggiunto per memorizzare il valore medio ponderato delle recensioni
    
    public Hotel(String id, String name, String description, String city, String phone, ArrayList<String> services) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.city = city;
        this.phone = phone;
        this.services = services;
        this.ratings = new ConcurrentHashMap<String, Review>();
    }

    public Hotel(String id, String name, String description, String city, String phone, ArrayList<String> services, ConcurrentHashMap<String, Review> ratings) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.city = city;
        this.phone = phone;
        this.services = services;
        this.ratings = ratings;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCity() {
        return city;
    }

    public String getPhone() {
        return phone;
    }

    public ArrayList<String> getServices() {
        return services;
    }

    public ConcurrentHashMap<String, Review> getRatings() {
        return ratings;
    }


    /**
     * Metodo che calcola media pesata delle recensioni di un Hotel. In particolare
     * moltiplica ciascuna recensione per il suo peso e poi fa una media aritmetica.
     *
     * @return doble che rappresenta la media pesata calcolata, o 0 se non ci sono recensioni associate all'hotel.
     */
    private double weightedAverageCalculationReviews() {
        Collection<Review> reviews = this.ratings.values();
        if (reviews.isEmpty()) {
            return 0;
        }
        double weightedSum = 0;
        for (Review review : reviews) {
            double weight = review.getWeightReview();
            double averageRatingReview = (review.getPositionVote() + review.getPriceVote() + review.getCleanlinessVote() + review.getServiceVote() + review.getVotoComplessivo())/5.0;
            weightedSum +=  averageRatingReview * weight;
        }

        return weightedSum / reviews.size();
    }

    // Ritorna il valore della media pesata delle recensioni dell'Hotel.
    public double getWeightedAverageReviews() {
        if (weightedAverageReviews == null) { // Se il valore non è ancora stato calcolato, lo calcola
            weightedAverageReviews = weightedAverageCalculationReviews();
        }
        return weightedAverageReviews;
    }
    
    // Restituisce il numero di recensioni che ha questo hotel.
    public int get_nReview() {
        return this.ratings.size();
    }
    
    // Aggiunge una review ad un hotel.
    public void addReview(int[] scores){
        Review review = new Review(scores[0], scores[1], scores[2], scores[3],scores[4]);
        this.ratings.put(Integer.toString(review.getId()), review);
        this.weightedAverageReviews = null; // Metto il valore della media ponderata a null così da dover essere ricalcolato quando necessario.
    }

    @Override
    public String toString() {
        String risp = "Hotel{"+ "name=" + name + " weightedAverage=" + this.getWeightedAverageReviews() + ", description=" + description + ", city=" + city + ", phone=" + phone + ", services=" + services.toString() + ", ratings={";
        for(Map.Entry<String, Review> entry : this.ratings.entrySet()) {
            risp = risp + "{" + entry.getValue().toString() + "}";
        }
        risp = risp + "}";
        return risp;
    }

}
