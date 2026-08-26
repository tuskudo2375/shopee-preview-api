package titus.molaai;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Surface;

import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

public final class CameraController {
    public static final class LensSpec {
        public String openId;
        public String physicalId;
        public float eqMm;
        public float focalMm;
        public float digitalZoom = 1f;
        public String label;
        public boolean hardware;
        public String role;

        public String detail() {
            String id = openId == null ? "?" : openId;
            return label + "×  " + role + "  SAT zoom=" + fmt(digitalZoom) + "×  logical=" + id;
        }
        private static String fmt(float v) { return String.format(Locale.US, "%.2f", v); }
    }

    public interface Listener {
        void onLensesChanged(List<LensSpec> lenses, int selectedIndex);
        void onStatus(String message);
        void onPhotoSaved(Uri uri);
        void onError(String message);
    }

    private final Activity activity;
    private final PreviewView previewView;
    private final Listener listener;
    private final CameraManager cameraManager;
    private final Executor mainExecutor;

    private ProcessCameraProvider provider;
    private Camera camera;
    private ImageCapture imageCapture;
    private List<LensSpec> lenses = new ArrayList<>();
    private int selectedIndex = 0;
    private int facing = CameraSelector.LENS_FACING_BACK;
    private String logicalCameraId = "";
    private String physicalIds = "";
    private float minZoom = 1f;
    private float maxZoom = 1f;

    public CameraController(Activity activity, PreviewView previewView, Listener listener) {
        this.activity = activity;
        this.previewView = previewView;
        this.listener = listener;
        this.cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        this.mainExecutor = ContextCompat.getMainExecutor(activity);
    }

    public void start() {
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                provider = future.get();
                bindCamera();
            } catch (Exception e) {
                listener.onError("Không khởi tạo được CameraX: " + e.getMessage());
            }
        }, mainExecutor);
    }

    public void stop() {
        if (provider != null) provider.unbindAll();
        camera = null;
        imageCapture = null;
    }

    public void toggleFacing() {
        facing = facing == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        bindCamera();
    }

    public void selectLens(int index) {
        if (camera == null || index < 0 || index >= lenses.size()) return;
        selectedIndex = index;
        LensSpec lens = lenses.get(index);
        camera.getCameraControl().setZoomRatio(lens.digitalZoom);
        listener.onLensesChanged(new ArrayList<>(lenses), selectedIndex);
        listener.onStatus("SAT " + lens.label + "× • " + lens.role);
    }

    public void takePhoto() {
        if (imageCapture == null) return;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "AI_CAMERA_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AI Camera");

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
                activity.getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();
        imageCapture.takePicture(options, mainExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults output) {
                listener.onPhotoSaved(output.getSavedUri());
            }
            @Override public void onError(androidx.camera.core.ImageCaptureException exc) {
                listener.onError("Chụp ảnh lỗi: " + exc.getMessage());
            }
        });
    }

    public String diagnostics() {
        StringBuilder s = new StringBuilder();
        s.append("Engine: CameraX + ColorOS logical SAT\n");
        s.append("Facing: ").append(facing == CameraSelector.LENS_FACING_BACK ? "BACK" : "FRONT").append('\n');
        s.append("Logical camera: ").append(logicalCameraId.isEmpty() ? "?" : logicalCameraId).append('\n');
        if (!physicalIds.isEmpty()) s.append("Physical children: ").append(physicalIds).append('\n');
        s.append("Zoom range: ").append(fmt(minZoom)).append(" → ").append(fmt(maxZoom)).append("\n");
        for (int i = 0; i < lenses.size(); i++) {
            s.append(i == selectedIndex ? "▶ " : "  ").append(lenses.get(i).detail()).append('\n');
        }
        return s.toString().trim();
    }

    private void bindCamera() {
        if (provider == null || activity.isFinishing()) return;
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            provider.unbindAll();
            CameraSelector selector = new CameraSelector.Builder().requireLensFacing(facing).build();
            int rotation = previewView.getDisplay() != null ? previewView.getDisplay().getRotation() : Surface.ROTATION_0;

            Preview preview = new Preview.Builder().setTargetRotation(rotation).build();
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(rotation)
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            camera = provider.bindToLifecycle((LifecycleOwner) activity, selector, preview, imageCapture);
            discoverLogicalCamera();
            rebuildLensSlots();
            selectedIndex = chooseOneX();
            listener.onLensesChanged(new ArrayList<>(lenses), selectedIndex);
            if (!lenses.isEmpty()) camera.getCameraControl().setZoomRatio(lenses.get(selectedIndex).digitalZoom);
            listener.onStatus("CameraX SAT • logical " + (logicalCameraId.isEmpty() ? "?" : logicalCameraId));
        } catch (Exception e) {
            listener.onError("Bind camera lỗi: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void discoverLogicalCamera() {
        logicalCameraId = "";
        physicalIds = "";
        try {
            logicalCameraId = Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(logicalCameraId);
            Set<String> ids = c.getPhysicalCameraIds();
            if (ids != null && !ids.isEmpty()) physicalIds = ids.toString();
        } catch (Exception ignored) {}
    }

    private void rebuildLensSlots() {
        lenses = new ArrayList<>();
        ZoomState z = camera == null ? null : camera.getCameraInfo().getZoomState().getValue();
        minZoom = z == null ? 1f : z.getMinZoomRatio();
        maxZoom = z == null ? 1f : z.getMaxZoomRatio();

        if (facing == CameraSelector.LENS_FACING_FRONT) {
            addSlot(clamp(1f), "1", "FRONT");
            return;
        }

        if (minZoom < 0.95f) {
            float uw = minZoom <= 0.62f ? 0.6f : minZoom;
            addSlot(clamp(uw), trimLabel(uw), "ULTRA-WIDE / SAT");
        }
        addSlot(clamp(1f), "1", "MAIN");
        if (maxZoom >= 1.95f) addSlot(clamp(2f), "2", "MAIN CROP / SAT");
        if (maxZoom >= 2.85f) addSlot(clamp(3f), "3", "TELE / SAT");
        if (lenses.isEmpty()) addSlot(clamp(1f), "1", "MAIN");
    }

    private void addSlot(float zoom, String label, String role) {
        for (LensSpec x : lenses) if (Math.abs(x.digitalZoom - zoom) < 0.03f) return;
        LensSpec l = new LensSpec();
        l.openId = logicalCameraId;
        l.physicalId = null;
        l.digitalZoom = zoom;
        l.label = label;
        l.role = role;
        l.hardware = true;
        lenses.add(l);
    }

    private int chooseOneX() {
        for (int i = 0; i < lenses.size(); i++) if (Math.abs(lenses.get(i).digitalZoom - 1f) < 0.03f) return i;
        return 0;
    }

    private float clamp(float v) { return Math.max(minZoom, Math.min(maxZoom, v)); }
    private static String trimLabel(float v) {
        if (Math.abs(v - Math.round(v)) < 0.03f) return Integer.toString(Math.round(v));
        return String.format(Locale.US, "%.1f", v);
    }
    private static String fmt(float v) { return String.format(Locale.US, "%.2f", v); }
}
