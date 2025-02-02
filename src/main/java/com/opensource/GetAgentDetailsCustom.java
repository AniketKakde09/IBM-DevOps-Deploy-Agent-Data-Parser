package com.opensource;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.io.*;
import java.util.List;
import java.util.Map;

public class GetAgentDetailsCustom {

    // Configuration variables (replace with environment variables or config files)
    private static String USERNAME = "";
    private static String PASSWORD = "";
    private static String BASE_URL = "https://localhost:8443";
    private static boolean ACCEPT_SELF_SIGNED_CERTIFICATE = true;
    private static String INPUT_PROPERTIES = "";

    private static boolean EXPORT_DATA_INTO_CSV = true;
    private static boolean isLoggedIn = false;
    private static String sessionAuthHeader = ""; // Authorization header for session
    private static Map<String, List<String>> agentData = new LinkedHashMap<>();

    private static String FILE_PATH = "";
    public GetAgentDetailsCustom(String username, String password, String baseUrl, boolean acceptSelfSignedCertificate, String input_properties, boolean export_data_into_csv, String filepath) {
        this.USERNAME = username;
        this.PASSWORD = password;
        this.BASE_URL = baseUrl;

        this.ACCEPT_SELF_SIGNED_CERTIFICATE = acceptSelfSignedCertificate;
        this.INPUT_PROPERTIES = input_properties;
        this.EXPORT_DATA_INTO_CSV = export_data_into_csv;
        this.FILE_PATH=filepath;
    }

    public static void main(String[] args) {
        // set properties
        //get the working directory first
        String workingDir = System.getProperty("user.dir");
        System.out.println("Working Directory : " + workingDir);

        String propsFilePath = args[0];
        Properties prop = new Properties();
        InputStream input = null;

        try {
            input = new FileInputStream(propsFilePath);
            // load a properties file
            prop.load(input);
            // get the property value and print it out
            String UCDURL = prop.getProperty("UCD_URL");
            String UCDUsername = prop.getProperty("UCD_Username");
            String UCDPassword = prop.getProperty("UCD_Password");
            String ListString = prop.getProperty("Agent_Property_List");
            boolean CreateCSVFile=Boolean.parseBoolean(prop.getProperty("Export_Data_Into_CSV", "false"));
            String FileName = prop.getProperty("File_Path");
            boolean acceptSelfSignedCertificate = Boolean.parseBoolean(prop.getProperty("ACCEPT_SELF_SIGNED_CERTIFICATE", "false"));

            GetAgentDetailsCustom obj = new GetAgentDetailsCustom(
                    UCDUsername,
                    UCDPassword,
                    UCDURL,
                    acceptSelfSignedCertificate,
                    ListString,
                    CreateCSVFile,
                    FileName
            );

            // Perform login once
            if (!isLoggedIn) {
                login();
            }

            // List of properties to fetch
            List<String> propertiesList = Arrays.asList(INPUT_PROPERTIES.split("\n"));

            // Fetch agents data
            ResponseData agentResponse = executeGetRequest(BASE_URL + "/cli/agentCLI");
            if (agentResponse == null || agentResponse.getResponseCode() != HttpURLConnection.HTTP_OK) {
                System.err.println("Failed to fetch agent data. Exiting...");
                return;
            }

            JSONArray jsonArray = new JSONArray(agentResponse.getResponseBody());
            System.out.println("-------------------------------------------------------");
            // Initialize headers in the map
            if (agentData.isEmpty()) {
                List<String> headers = new ArrayList<>(List.of("Name", "Status", "License Type", "Version", "Communication Version"));
                headers.addAll(propertiesList);
                agentData.put("ID", headers);
                System.out.println("Agent ID :- ID" + " Properties :- " + headers);
            }

            // Process agents and fetch their properties
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject agent = jsonArray.getJSONObject(i);
                String agentId = agent.getString("id");
                List<String> agentDetails = new ArrayList<>(List.of(
                        agent.getString("name"),
                        agent.getString("status"),
                        agent.getString("licenseType"),
                        agent.getString("version"),
                        agent.getString("communicationVersion")
                ));

                // Fetch additional properties for the agent
                for (String property : propertiesList) {
                    try {
                        String encodedProperty = URLEncoder.encode(property, "UTF-8");
                        ResponseData propertyResponse = executeGetRequest(BASE_URL + "/cli/agentCLI/getProperty?agent=" + agentId + "&name=" + encodedProperty);
                        agentDetails.add((propertyResponse != null && !propertyResponse.getResponseBody().isEmpty()) ? propertyResponse.getResponseBody() : "NA");
                    } catch (UnsupportedEncodingException e) {
                        System.err.println("Error encoding property: " + property);
                        agentDetails.add("Error");
                    }
                }

                agentData.put(agentId, agentDetails);
                System.out.println("Agent ID :- " + agentId + " Properties :- " + agentDetails);
            }

            System.out.println("-------------------------------------------------------");
            if (EXPORT_DATA_INTO_CSV){
                if (FileName.endsWith(".csv")){
                    System.out.println("Data will be written into file :- " + FileName);
                    WriteAgentDataIntoCSV(FileName,agentData);
                } else {
                    System.out.println("Error please make sure file path has file name with extension .csv");
                }
            }
            System.exit(0);

        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }

    // Login method
    private static void login() {
        try {
            String loginUrl = BASE_URL + "/cli/application";
            ResponseData response = sendGetRequest(loginUrl, USERNAME, PASSWORD, ACCEPT_SELF_SIGNED_CERTIFICATE);
            if (response.getResponseCode() == HttpURLConnection.HTTP_OK) {
                System.out.println("Login successful.");
                isLoggedIn = true;
            } else {
                System.err.println("Login failed. Response code: " + response.getResponseCode());
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error during login: " + e.getMessage());
            System.exit(1);
        }
    }

    // Method to execute GET requests
    private static ResponseData executeGetRequest(String requestUrl) {
        return sendGetRequest(requestUrl, USERNAME, PASSWORD, ACCEPT_SELF_SIGNED_CERTIFICATE);
    }

    // Method to send GET requests
    private static ResponseData sendGetRequest(String urlString, String username, String password, boolean acceptSelfSigned) {
        HttpURLConnection connection = null;
        StringBuilder response = new StringBuilder();
        int responseCode = -1;

        try {
            if (acceptSelfSigned) {
                disableCertificateValidation();
            }

            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if (isLoggedIn && !sessionAuthHeader.isEmpty()) {
                connection.setRequestProperty("Authorization", sessionAuthHeader);
            } else {
                String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
                sessionAuthHeader = "Basic " + auth;
                connection.setRequestProperty("Authorization", sessionAuthHeader);
            }

            responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error during GET request: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return new ResponseData(response.toString(), responseCode);
    }

    // Disable certificate validation
    private static void disableCertificateValidation() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            }};
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.err.println("Error disabling certificate validation: " + e.getMessage());
        }
    }

    // Method to print agent data
    private static void printAgentData() {
        for (Map.Entry<String, List<String>> entry : agentData.entrySet()) {
            System.out.println("Agent ID: " + entry.getKey() + " Properties: " + entry.getValue());
        }
    }

    private static void WriteAgentDataIntoCSV(String filePath,Map<String, List<String>> agentData) {
        File file = new File(filePath);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Error creating file: " + e.getMessage());
            return;
        }

        try (FileWriter writer = new FileWriter(filePath)) {
            for (Map.Entry<String, List<String>> entry : agentData.entrySet()) {
                String agent = entry.getKey();
                List<String> tasks = entry.getValue();
                writer.append(agent);
                for (String task : tasks) {
                    writer.append(","+task);
                }
                writer.append("\n");
            }
            System.out.println("CSV file written successfully: " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }
    }

    // ResponseData class
    static class ResponseData {
        private final String responseBody;
        private final int responseCode;

        public ResponseData(String responseBody, int responseCode) {
            this.responseBody = responseBody;
            this.responseCode = responseCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public int getResponseCode() {
            return responseCode;
        }
    }
}
