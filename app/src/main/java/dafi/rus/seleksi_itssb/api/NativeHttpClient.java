package dafi.rus.seleksi_itssb.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class NativeHttpClient {

    public interface Callback {
        void onSuccess(int statusCode, String response);
        void onError(int statusCode, String errorMessage);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void get(Context context, String endpoint, Callback callback) {
        request(context, "GET", endpoint, null, callback);
    }

    public static void post(Context context, String endpoint, String jsonBody, Callback callback) {
        request(context, "POST", endpoint, jsonBody, callback);
    }

    public static void put(Context context, String endpoint, String jsonBody, Callback callback) {
        request(context, "PUT", endpoint, jsonBody, callback);
    }

    public static void delete(Context context, String endpoint, Callback callback) {
        request(context, "DELETE", endpoint, null, callback);
    }

    private static void request(Context context, String method, String endpoint, String jsonBody, Callback callback) {
            EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String fullUrl = "http://localhost:5000/" + (endpoint.startsWith("/") ? endpoint.substring(1) : endpoint);
                URL url = new URL(fullUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/json");

                String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiIxIiwiZW1haWwiOiJidWRpLnNhbnRvc29Ac2FuZ3UudGVzdCIsIm5hbWUiOiJCdWRpIFNhbnRvc28iLCJleHAiOjE3OTIyMjY1NDYsImlzcyI6IlNhbmd1QXBpIiwiYXVkIjoiU2FuZ3VBcHAifQ.SvdmxH5hZ4Q0NUCQkkOWOrw1Rg-KPrSUHWpKTJme9R4";
                if (token != null && !token.trim().isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }

                if (jsonBody != null && (method.equals("POST") || method.equals("PUT"))) {
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setDoOutput(true);
                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }
                }

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300) ? conn.getInputStream() : conn.getErrorStream();

                StringBuilder responseStr = new StringBuilder();
                if (is != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseStr.append(line);
                        }
                    }
                }

                String result = responseStr.toString();
                MAIN_HANDLER.post(() -> {
                    if (statusCode >= 200 && statusCode < 300) {
                        callback.onSuccess(statusCode, result);
                    } else {
                        String errMsg = extractMessage(result, statusCode);
                        callback.onError(statusCode, errMsg);
                    }
                });

            } catch (Exception e) {
                MAIN_HANDLER.post(() -> callback.onError(0, "Koneksi gagal: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static String extractMessage(String json, int code) {
        if (json == null || json.isEmpty()) return "Error (" + code + ")";
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("message")) return obj.getString("message");
            if (obj.has("error")) return obj.getString("error");
        } catch (Exception ignored) {}
        return "Error (" + code + "): " + json;
    }
}