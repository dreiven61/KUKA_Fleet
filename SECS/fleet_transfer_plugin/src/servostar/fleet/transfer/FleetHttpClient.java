package servostar.fleet.transfer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Fixed same-container destination; no token persistence, redirects, or POST replay. */
public final class FleetHttpClient implements TransferService.FleetClient {
    private final ObjectMapper json = new ObjectMapper();
    private final String base;
    public FleetHttpClient() { this("http://127.0.0.1:10870/interfaces/api/amr/"); }
    // Package-private override is solely for loopback mock tests, never HTTP input.
    FleetHttpClient(String base) { this.base = base; }

    public Map<String,Object> post(String path, String token, Map<String,Object> body) throws Exception {
        if (!"robotQuery".equals(path) && !"submitMission".equals(path)
                && !"jobQuery".equals(path) && !"containerQuery".equals(path)
                && !"missionCancel".equals(path) && !"pauseMission".equals(path) && !"recoverMission".equals(path)
                && !"containerIn".equals(path) && !"containerOut".equals(path) && !"containerQueryAll".equals(path)) throw new IllegalArgumentException("Unsupported API.");
        HttpURLConnection connection = (HttpURLConnection)new URL(base + path).openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", token);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        byte[] encoded = json.writeValueAsBytes(body);
        // Streaming mode prevents HttpURLConnection from automatically replaying a POST.
        connection.setFixedLengthStreamingMode(encoded.length);
        try {
            try (OutputStream out = connection.getOutputStream()) { out.write(encoded); }
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new SecurityException("Fleet rejected Authorization.");
            if (status < 200 || status >= 300) throw new IOException("Fleet HTTP status " + status + ". Query COMMANDID if submission was attempted.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1048576) throw new IOException("Fleet response exceeds 1 MiB.");
                    output.write(buffer, 0, count);
                }
                return json.readValue(new String(output.toByteArray(), StandardCharsets.UTF_8), new TypeReference<Map<String,Object>>() {});
            }
        } finally { connection.disconnect(); }
    }
}
