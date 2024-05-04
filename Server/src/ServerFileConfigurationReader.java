import java.util.HashMap;

public class ServerFileConfigurationReader extends FileConfigurationReader {
    private static final String SERVER_CONFIGURATION_FILE_PATH = "Files/Configuration/Server_Configuration.txt";

    private static final HashMap<String, String> serverConfigMap = readConfigurationFile(SERVER_CONFIGURATION_FILE_PATH);

    public static String getIp() {
        return serverConfigMap.get("Ip");
    }

    public static int getPort() {
        System.out.println("Numero di porta server: " + serverConfigMap.get("Port"));
        return Integer.parseInt(serverConfigMap.get("Port"));
    }
    public static String getMulticastAddress() {
        return serverConfigMap.get("Multicast_Address");
    }

    public static String getMulticastPort() {
        return serverConfigMap.get("Multicast_Port");
    }

    public static String get_Waiting_Seconds_Ranking_Recalculation() {
        return serverConfigMap.get("Waiting_Seconds_Ranking_Recalculation");
    }

    public static String get_Waiting_Seconds_File_Update() {
        return serverConfigMap.get("Waiting_Seconds_File_Update");
    }

}
