import java.util.ArrayList;
import java.util.Map;

/**
 * Classe utilizzata soltanto per il parsing dal file Hotels.json.
 */

public class StartHotel {
    private final String id;
    private final String name;
    private final String description;
    private final String city;
    private String phone;
    private ArrayList<String> services;
    private int rate;
    private Map<String, Integer> ratings;
    
    public StartHotel(String id, String name, String description, String city, String phone, ArrayList<String> services, Map<String, Integer> ratings) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.city = city;
        this.phone = phone;
        this.services = services;
        this.rate = rate;
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

    public int getRate() {
        return rate;
    }

    public Map<String, Integer> getRatings() {
        return ratings;
    }
    
    
    
}