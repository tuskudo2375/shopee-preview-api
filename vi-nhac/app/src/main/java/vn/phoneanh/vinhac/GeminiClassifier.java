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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class GeminiClassifier {
    static final class Classification {
        final String category;
        final String source;

        Classification(String category, String source) {
            this.category = category;
            this.source = source;
        }
    }

    interface Callback {
        void done(Classification result, boolean usedAi);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    static void classifyAsync(Context c, String raw, String fallbackCategory, String fallbackSource,
                              List<String> categories, List<String> sources, Callback cb) {
        IO.execute(() -> {
            Classification result = classify(c, raw, fallbackCategory, fallbackSource, categories, sources);
            new Handler(Looper.getMainLooper()).post(() -> cb.done(result,
                    !result.category.equals(fallbackCategory) || !result.source.equals(fallbackSource)));
        });
    }

    static Classification classify(Context c, String raw, String fallbackCategory, String fallbackSource) {
        return classify(c, raw, fallbackCategory, fallbackSource,
                Arrays.asList(BudgetStore.DEFAULT_CATEGORIES), Arrays.asList(BudgetStore.DEFAULT_SOURCES));
    }

    static Classification classify(Context c, String raw, String fallbackCategory, String fallbackSource,
                                   List<String> categories, List<String> sources) {
        String key = new SecretStore(c).getApiKey();
        if (key.isEmpty()) return new Classification(fallbackCategory, fallbackSource);

        String explicitSource = ExpenseParser.explicitSource(raw, sources);
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", key);
            conn.setDoOutput(true);

            String prompt = "Bạn là bộ phân loại chi tiêu tiếng Việt cho một ứng dụng quản lý ngân sách. "
                    + "Hãy chọn đúng một danh mục và một nguồn tiền trong các danh sách cho phép, chỉ trả JSON hợp lệ, không giải thích.\n"
                    + "Danh mục được phép: " + join(categories) + "\n"
                    + "Nguồn tiền được phép: " + join(sources) + "\n"
                    + "Quy tắc ưu tiên:\n"
                    + "1) Hiểu nghĩa khoản chi, không phân loại theo từ 'bank' nếu nó không phải nguồn tiền. 'ăn trưa', 'ăn tối', 'trà sữa', 'cà phê', 'cà hê', 'cafe' luôn thuộc danh mục Ăn uống hoặc nhãn tùy chỉnh tương ứng.\n"
                    + "2) Nguồn tiền độc lập với danh mục: 'bank', 'banking', 'CK', 'chuyển khoản' = Chuyển khoản; 'TP', 'thẻ TP', 'TPBank' = Thẻ TP; 'Tech', 'TCB', 'Techcombank' = Thẻ Tech; 'VIB', 'thẻ VIB' = Thẻ VIB; 'tiền mặt', 'cash' = Tiền Mặt.\n"
                    + "3) Nếu không có tín hiệu nguồn tiền rõ ràng trong câu, bắt buộc giữ Nguồn mặc định và không được tự đoán nguồn từ nội dung món ăn.\n"
                    + "4) Có thể hiểu viết tắt, không dấu, tiếng teen và lỗi gõ nhẹ; đối chiếu với nhãn gần nghĩa nhất trong danh sách.\n"
                    + "5) Nếu bộ lọc offline đã nhận ra danh mục rõ ràng thì ưu tiên danh mục đó, trừ khi nội dung có bằng chứng mạnh hơn cho danh mục khác.\n"
                    + "Khoản nhập nguyên bản: " + raw + "\n"
                    + "Danh mục offline gợi ý: " + fallbackCategory + "\n"
                    + "Nguồn mặc định: " + fallbackSource;

            JSONObject body = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));
            contents.put(new JSONObject().put("parts", parts));
            body.put("contents", contents);

            JSONObject props = new JSONObject()
                    .put("category", new JSONObject().put("type", "string").put("enum", new JSONArray(categories)))
                    .put("source", new JSONObject().put("type", "string").put("enum", new JSONArray(sources)));
            JSONObject schema = new JSONObject()
                    .put("type", "object")
                    .put("properties", props)
                    .put("required", new JSONArray().put("category").put("source"));
            body.put("generationConfig", new JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseJsonSchema", schema)
                    .put("temperature", 0));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() / 100 != 2) return new Classification(fallbackCategory, fallbackSource);

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) response.append(line);
            }
            String modelText = new JSONObject(response.toString())
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text");
            JSONObject result = new JSONObject(cleanJson(modelText));
            String category = result.optString("category", fallbackCategory);
            String source = result.optString("source", fallbackSource);

            if (!categories.contains(category)) category = fallbackCategory;
            // Never allow the model to invent a source when the user did not write one.
            source = explicitSource.isEmpty() ? fallbackSource : explicitSource;
            if (!sources.contains(source)) source = fallbackSource;
            return new Classification(category, source);
        } catch (Exception ignored) {
            return new Classification(fallbackCategory, fallbackSource);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append(", ");
            result.append('"').append(values.get(i)).append('"');
        }
        return result.toString();
    }

    private static String cleanJson(String text) {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) value = value.substring(firstNewline + 1, lastFence).trim();
        }
        return value;
    }
}
