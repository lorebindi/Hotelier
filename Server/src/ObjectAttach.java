public class ObjectAttach {
    private int operation; // Attributo utilizzato per mantenere l'id dell'operazione richiesta.
    private int output; // Attributo utilizzato per mantenere l'output dell'operazione richiesta.
    private String username; // Attributo utilizzato per mantenere l'username.
    private String messagge; // Attributo utilizzato per mantenere i messaggi tra client e server.
    
    public ObjectAttach() {
        this.operation = -1;
        this.output = -1;
        this.username = "";
        this.messagge = "";
    }
    
    public ObjectAttach(int operation, int output) {
        this.operation = operation;
        this.output = output;
        this.username = "";
        this.messagge = "";
    }
    
    public ObjectAttach(int operation, int output, String username) {
        this.operation = operation;
        this.output = output;
        this.username = username;
        this.messagge = "";
    }
    
    public ObjectAttach(int operation, int output, String username, String messagge) {
        this.operation = operation;
        this.output = output;
        this.username = username;
        this.messagge = messagge;
    }

    public int getOperation() {
        return this.operation;
    }

    public int getOutput() {
        return this.output;
    }
    
    public String getUsername() {
        return this.username;
    }

    public String getMessagge() {
        return messagge;
    }
    
    public void setOperation(int operation) {
        this.operation = operation;
    }

    public void setOutput(int output) {
        this.output = output;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public void setMessagge(String messagge) {
        this.messagge = messagge;
    }
    
    
}
