import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class FileConfigurationReader {
    protected static HashMap<String,String> readConfigurationFile(String filePath)  {
        HashMap<String,String> configMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2)
                    configMap.put(parts[0].trim(), parts[1].trim());
            }
        }
        catch (IOException e) {
            System.err.println("IO Error from readConfigurationFile method");
        }
        return configMap;
    }

}
