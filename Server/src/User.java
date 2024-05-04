
public class User {
    
    private final String username;
    private final String hashedPassword;
    private String badge;
    private int nRecensioni;
    
    public User (String username, String hashedPassword) {
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.badge = "Recensore";
        this.nRecensioni = 0;
    }
    
    public User (String username, String hashedPassword, String badge, int nRecensioni) {
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.badge = "Recensore";
        this.nRecensioni = 0;
    }

    public String getUsername() {
        return this.username;
    }

    public String getHashedPassword() {
        return this.hashedPassword;
    }

    public String getBadge() {
        return this.badge;
    }

    public int getnRecensioni() {
        return this.nRecensioni;
    }

    public void addRecensione() {
        this.nRecensioni++;
        // Cambio di badge
        if(this.nRecensioni > 5 && this.nRecensioni < 10)
            this.badge = "Recensore Esperto";
        else if (this.nRecensioni > 10 && this.nRecensioni < 15)
            this.badge = "Contributore";
        else if (this.nRecensioni > 15 && this.nRecensioni < 20)
            this.badge = "Contributore Esperto";
        else if (this.nRecensioni > 20)
            this.badge = "Contributore Super";
    }
    
    
    
    
    
}
