package titus.molaai;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.media.FaceDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight on-device subject finder used only to seed SubjectTracker.
 * No network/API: face-first, then saliency fallback.
 */
public final class LocalSubjectAnalyzer {
    public static final class Result {
        public float[] box;
        public float targetX = 0.5f;
        public float targetY = 0.5f;
        public float confidence;
        public String kind = "Chủ thể";
        public String tip = "Đã khóa chủ thể • căn vào khung";
    }

    public Result analyze(Bitmap input) {
        if (input == null || input.getWidth() < 16 || input.getHeight() < 16) return null;
        Result face = analyzeFaces(input);
        if (face != null) return face;
        return analyzeSaliency(input);
    }

    private Result analyzeFaces(Bitmap input) {
        Bitmap work = fit(input, 420);
        if ((work.getWidth() & 1) != 0) {
            Bitmap even = Bitmap.createScaledBitmap(work, work.getWidth() - 1, work.getHeight(), true);
            if (work != input) work.recycle();
            work = even;
        }
        Bitmap rgb565 = work.getConfig() == Bitmap.Config.RGB_565 ? work : work.copy(Bitmap.Config.RGB_565, false);
        if (rgb565 == null) {
            if (work != input) work.recycle();
            return null;
        }

        FaceDetector.Face[] faces = new FaceDetector.Face[5];
        int count;
        try {
            count = new FaceDetector(rgb565.getWidth(), rgb565.getHeight(), faces).findFaces(rgb565, faces);
        } catch (Throwable t) {
            if (rgb565 != work) rgb565.recycle();
            if (work != input) work.recycle();
            return null;
        }

        if (count <= 0) {
            if (rgb565 != work) rgb565.recycle();
            if (work != input) work.recycle();
            return null;
        }

        int w = rgb565.getWidth(), h = rgb565.getHeight();
        List<float[]> boxes = new ArrayList<>();
        float confidence = 0f;
        for (int i = 0; i < count; i++) {
            FaceDetector.Face f = faces[i];
            if (f == null) continue;
            PointF mid = new PointF();
            f.getMidPoint(mid);
            float eye = f.eyesDistance();
            if (eye < 3f) continue;

            float bw = eye * 4.2f;
            float bh = eye * 6.2f;
            float left = mid.x - bw * 0.5f;
            float top = mid.y - eye * 1.55f;
            boxes.add(normalizeBox(left, top, bw, bh, w, h));
            confidence = Math.max(confidence, clamp01(eye / Math.max(24f, Math.min(w, h) * 0.18f)));
        }

        if (rgb565 != work) rgb565.recycle();
        if (work != input) work.recycle();
        if (boxes.isEmpty()) return null;

        float[] union = union(boxes);
        union = expand(union, boxes.size() > 1 ? 0.08f : 0.05f, boxes.size() > 1 ? 0.10f : 0.06f);

        Result r = new Result();
        r.box = union;
        r.kind = boxes.size() > 1 ? "Nhóm người" : "Chân dung";
        r.tip = boxes.size() > 1 ? "Đã khóa nhóm người • căn khung" : "Đã khóa chân dung • căn khung";
        r.confidence = Math.max(0.55f, confidence);
        r.targetX = 0.5f;
        r.targetY = 0.5f;
        return r;
    }

    private Result analyzeSaliency(Bitmap input) {
        Bitmap b = fit(input, 180);
        int w = b.getWidth(), h = b.getHeight();
        if (w < 8 || h < 8) {
            if (b != input) b.recycle();
            return null;
        }

        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        float[] lum = new float[px.length];
        float[] sat = new float[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            float rr = Color.red(c), gg = Color.green(c), bb = Color.blue(c);
            lum[i] = 0.2126f * rr + 0.7152f * gg + 0.0722f * bb;
            float max = Math.max(rr, Math.max(gg, bb));
            float min = Math.min(rr, Math.min(gg, bb));
            sat[i] = max <= 1f ? 0f : (max - min) / max * 255f;
        }

        double sum = 0, sum2 = 0;
        int n = 0;
        float[] score = new float[px.length];
        for (int y = 2; y < h - 2; y += 2) {
            for (int x = 2; x < w - 2; x += 2) {
                int i = y * w + x;
                float g = Math.abs(lum[i] - lum[i - 2]) + Math.abs(lum[i] - lum[i + 2])
                        + Math.abs(lum[i] - lum[i - 2 * w]) + Math.abs(lum[i] - lum[i + 2 * w]);
                float nx = x / (float) w - 0.5f, ny = y / (float) h - 0.5f;
                float centerPrior = 1f - 0.38f * Math.min(1f, (float)Math.sqrt(nx * nx + ny * ny) / 0.7071f);
                float s = (g + sat[i] * 0.30f) * centerPrior;
                score[i] = s;
                sum += s;
                sum2 += s * s;
                n++;
            }
        }
        if (b != input) b.recycle();
        if (n < 8) return null;

        double mean = sum / n;
        double var = Math.max(0, sum2 / n - mean * mean);
        double threshold = mean + Math.sqrt(var) * 0.60;

        double sw = 0, sx = 0, sy = 0;
        for (int y = 2; y < h - 2; y += 2) {
            for (int x = 2; x < w - 2; x += 2) {
                int i = y * w + x;
                float s = score[i];
                if (s < threshold) continue;
                double wt = Math.max(1.0, s - threshold + 1.0);
                sw += wt;
                sx += wt * x;
                sy += wt * y;
            }
        }

        if (sw <= 1.0) {
            Result r = new Result();
            r.box = new float[]{0.20f, 0.20f, 0.60f, 0.60f};
            r.kind = "Cảnh";
            r.tip = "Cảnh ít chủ thể rõ • căn bố cục tổng thể";
            r.confidence = 0.25f;
            return r;
        }

        double cx = sx / sw, cy = sy / sw;
        double vx = 0, vy = 0;
        for (int y = 2; y < h - 2; y += 2) {
            for (int x = 2; x < w - 2; x += 2) {
                int i = y * w + x;
                float s = score[i];
                if (s < threshold) continue;
                double wt = Math.max(1.0, s - threshold + 1.0);
                vx += wt * (x - cx) * (x - cx);
                vy += wt * (y - cy) * (y - cy);
            }
        }
        vx = Math.sqrt(vx / sw) / w;
        vy = Math.sqrt(vy / sw) / h;
        float bw = clamp((float)(vx * 4.4), 0.28f, 0.72f);
        float bh = clamp((float)(vy * 4.4), 0.28f, 0.72f);
        float ncx = (float)(cx / w), ncy = (float)(cy / h);

        Result r = new Result();
        r.box = clampBox(new float[]{ncx - bw / 2f, ncy - bh / 2f, bw, bh});
        r.kind = "Chủ thể";
        r.tip = "Đã khóa vùng nổi bật • căn vào giữa";
        r.confidence = (float)clamp((Math.sqrt(var) / (mean + 1.0)) * 0.7 + 0.30, 0.30, 0.88);
        r.targetX = 0.5f;
        r.targetY = 0.5f;
        return r;
    }

    private static Bitmap fit(Bitmap in, int max) {
        int w = in.getWidth(), h = in.getHeight();
        if (Math.max(w, h) <= max) return in;
        float f = max / (float)Math.max(w, h);
        int nw = Math.max(2, Math.round(w * f));
        int nh = Math.max(2, Math.round(h * f));
        if ((nw & 1) != 0) nw--;
        return Bitmap.createScaledBitmap(in, nw, nh, true);
    }

    private static float[] normalizeBox(float x, float y, float w, float h, int iw, int ih) {
        return clampBox(new float[]{x / iw, y / ih, w / iw, h / ih});
    }

    private static float[] union(List<float[]> boxes) {
        float l = 1f, t = 1f, r = 0f, b = 0f;
        for (float[] x : boxes) {
            l = Math.min(l, x[0]); t = Math.min(t, x[1]);
            r = Math.max(r, x[0] + x[2]); b = Math.max(b, x[1] + x[3]);
        }
        return clampBox(new float[]{l, t, r - l, b - t});
    }

    private static float[] expand(float[] b, float xPad, float yPad) {
        float x = b[0] - b[2] * xPad, y = b[1] - b[3] * yPad;
        float w = b[2] * (1f + xPad * 2f), h = b[3] * (1f + yPad * 2f);
        return clampBox(new float[]{x, y, w, h});
    }

    private static float[] clampBox(float[] b) {
        float w = clamp(b[2], 0.12f, 0.92f), h = clamp(b[3], 0.12f, 0.92f);
        float x = clamp(b[0], 0f, 1f - w), y = clamp(b[1], 0f, 1f - h);
        return new float[]{x, y, w, h};
    }

    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    private static float clamp01(float v) { return clamp(v, 0f, 1f); }
}
