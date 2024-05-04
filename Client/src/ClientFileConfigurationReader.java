import java.util.HashMap;

public class ClientFileConfigurationReader extends FileConfigurationReader {
    private static final String CLIENT_CONFIGURATION_FILE_PATH = "Files/Configuration/Client_Configuration.txt";
    private static final HashMap<String, String> clientConfigMap = FileConfigurationReader.readConfigurationFile(CLIENT_CONFIGURATION_FILE_PATH);

    public static String getClientIp() {
        return clientConfigMap.get("Ip");
    }

    public static int getClientPort() {
        return Integer.parseInt(clientConfigMap.get("Port"));
    }
    public static String getClientMulticastAddress() {
        return clientConfigMap.get("Multicast_Address");
    }

    public static String getClientMulticastPort() {
        return clientConfigMap.get("Multicast_Port");
    }

    public static int getClientTimeout(){ return Integer.parseInt(clientConfigMap.get("Timeout"));}

}
