package titus.expenseassistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class GeminiClassifier {
    static final class Classification {
        final String category;
        final String source;
        Classification(String category, String source) { this.category = category; this.source = source; }
    }
    interface Callback { void done(Classification result, boolean usedAi); }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static void classifyAsync(Context c, String raw, String fallbackCategory, String fallbackSource, Callback cb) {
        IO.execute(() -> { Classification result = classify(c, raw, fallbackCategory, fallbackSource); new Handler(Looper.getMainLooper()).post(() -> cb.done(result, !result.category.equals(fallbackCategory) || !result.source.equals(fallbackSource))); });
    }

    static Classification classify(Context c, String raw, String fallbackCategory, String fallbackSource) {
        String key = new SecretStore(c).getApiKey();
        if (key.isEmpty()) return new Classification(fallbackCategory, fallbackSource);
        String explicitSource = ExpenseParser.explicitSource(raw);
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent");
            conn = (HttpURLConnection) url.openConnection(); conn.setRequestMethod("POST"); conn.setConnectTimeout(7000); conn.setReadTimeout(10000); conn.setRequestProperty("Content-Type", "application/json"); conn.setRequestProperty("x-goog-api-key", key); conn.setDoOutput(true);
            String prompt = "Bạn là bộ phân loại chi tiêu tiếng Việt. Hãy phân loại đúng một mục tiêu và một nguồn tiền trong danh sách cho phép, chỉ trả JSON hợp lệ, không giải thích. "
                    + "Ưu tiên nghĩa trực tiếp của nội dung. 'ăn trưa', 'trà sữa', 'cà phê' luôn là Ăn uống; không được biến khoản ăn uống thành nguồn Chuyển khoản. "
                    + "Nguồn tiền là thông tin độc lập: chỉ chọn Chuyển khoản khi nội dung có rõ 'chuyển khoản', 'banking' hoặc 'CK'; chỉ chọn thẻ khi có rõ tên thẻ/ngân hàng. "
                    + "Nếu không có từ khóa nguồn tiền, bắt buộc dùng nguồn mặc định và tuyệt đối không tự đoán nguồn. "
                    + "Nếu bộ lọc offline đã nhận diện được mục tiêu thì giữ nguyên mục tiêu đó.\n"
                    + "Khoản chi: " + raw + "\nMục tiêu offline: " + fallbackCategory + "\nNguồn mặc định: " + fallbackSource;
            JSONObject body = new JSONObject(); JSONArray contents = new JSONArray(), parts = new JSONArray(); parts.put(new JSONObject().put("text", prompt)); contents.put(new JSONObject().put("parts", parts)); body.put("contents", contents);
            JSONObject props = new JSONObject().put("category", new JSONObject().put("type", "string").put("enum", new JSONArray(ExpenseParser.CATEGORIES))).put("source", new JSONObject().put("type", "string").put("enum", new JSONArray(ExpenseParser.SOURCES)));
            JSONObject schema = new JSONObject().put("type", "object").put("properties", props).put("required", new JSONArray().put("category").put("source"));
            body.put("generationConfig", new JSONObject().put("responseMimeType", "application/json").put("responseJsonSchema", schema).put("temperature", 0));
            try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            if (conn.getResponseCode() / 100 != 2) return new Classification(fallbackCategory, fallbackSource);
            StringBuilder response = new StringBuilder(); try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) { String line; while ((line = br.readLine()) != null) response.append(line); }
            String text = new JSONObject(response.toString()).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
            JSONObject result = new JSONObject(text); String category = result.getString("category"), source = result.getString("source");
            for (String allowed : ExpenseParser.CATEGORIES) if (allowed.equals(category)) {
                String finalCategory = "Chưa gắn thẻ".equals(fallbackCategory) ? category : fallbackCategory;
                String finalSource = explicitSource.isEmpty() ? fallbackSource : explicitSource;
                for (String allowedSource : ExpenseParser.SOURCES) if (allowedSource.equals(finalSource)) return new Classification(finalCategory, finalSource);
            }
        } catch (Exception ignored) {} finally { if (conn != null) conn.disconnect(); }
        return new Classification(fallbackCategory, fallbackSource);
    }
}
