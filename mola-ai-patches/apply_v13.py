from pathlib import Path

p = Path('mola-ai-camera/app/src/main/java/titus/molaai/MainActivity.java')
s = p.read_text(encoding='utf-8')

s = s.replace('import android.text.InputType;\n', '')
s = s.replace('import android.widget.EditText;\n', '')
s = s.replace('import java.io.ByteArrayOutputStream;\n', '')
s = s.replace('import android.widget.TextureView;', 'import androidx.camera.view.PreviewView;\nimport androidx.activity.ComponentActivity;')
if 'import java.util.concurrent.ExecutorService;' not in s:
    s = s.replace('import java.util.Locale;\n', 'import java.util.Locale;\nimport java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\n')

s = s.replace('public final class MainActivity extends Activity implements CameraController.Listener {',
              'public final class MainActivity extends ComponentActivity implements CameraController.Listener {')
s = s.replace('    private GeminiClient gemini;\n', '')
s = s.replace('    private TextureView texture;\n', '    private PreviewView texture;\n')
s = s.replace('    private SubjectTracker tracker;\n',
'''    private SubjectTracker tracker;\n    private final LocalSubjectAnalyzer localAnalyzer = new LocalSubjectAnalyzer();\n    private final ExecutorService localVision = Executors.newSingleThreadExecutor();\n    private float targetCx = 0.5f, targetCy = 0.5f;\n    private String subjectKind = \"\";\n''')

s = s.replace('        gemini = new GeminiClient();\n        immersive();\n        buildUi();', '        buildUi();\n        immersive();')
s = s.replace('        gemini = new GeminiClient();\n        buildUi();\n        immersive();', '        buildUi();\n        immersive();')
s = s.replace('        gemini = new GeminiClient();\n', '')
s = s.replace('WindowInsetsController c = getWindow().getInsetsController();', 'WindowInsetsController c = getWindow().getDecorView().getWindowInsetsController();')
s = s.replace('    @Override protected void onDestroy() { ui.removeCallbacksAndMessages(null); if (camera != null) camera.stop(); super.onDestroy(); }',
              '    @Override protected void onDestroy() { ui.removeCallbacksAndMessages(null); localVision.shutdownNow(); if (camera != null) camera.stop(); super.onDestroy(); }')

s = s.replace('        texture = new TextureView(this);\n        texture.setOpaque(false);',
'''        texture = new PreviewView(this);\n        texture.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);\n        texture.setScaleType(PreviewView.ScaleType.FILL_CENTER);''')
s = s.replace('texture == null || !texture.isAvailable()', 'texture == null || texture.getWidth() <= 0 || texture.getHeight() <= 0')
s = s.replace('if(!texture.isAvailable())return;', 'if(texture.getWidth()<=0||texture.getHeight()<=0)return;')
s = s.replace('Bitmap b = texture.getBitmap(w,h);', 'Bitmap b = previewBitmap(w,h);')

start = s.index('    private void toggleAi()')
end = s.index('    private void settings()')
new_ai = r'''    private void toggleAi(){if(aiBusy)return;if(aiActive){stopAi();return;}scanLocal();}

    private void scanLocal(){
        if(texture==null||texture.getWidth()<=0||texture.getHeight()<=0)return;
        Bitmap src=texture.getBitmap();
        if(src==null){toast("Preview chưa sẵn sàng");return;}
        Bitmap scaled=resize(src,480);
        if(scaled!=src)src.recycle();
        aiBusy=true;
        guide.showScanning("Đang tìm chủ thể trên máy…");
        setAiButtonState();

        localVision.execute(()->{
            LocalSubjectAnalyzer.Result r=null;
            String err=null;
            try{r=localAnalyzer.analyze(scaled);}
            catch(Throwable t){err=t.getClass().getSimpleName()+": "+t.getMessage();}
            finally{scaled.recycle();}
            final LocalSubjectAnalyzer.Result result=r;
            final String error=err;
            runOnUiThread(()->{
                aiBusy=false;
                if(result==null||result.box==null){
                    aiActive=false;
                    guide.clearGuide();
                    setAiButtonState();
                    toast(error==null?"Chưa tìm được chủ thể rõ ràng":"Nhận diện local lỗi: "+error);
                    return;
                }
                aiActive=true;
                trackerMisses=0;
                centeredSince=0;
                targetCx=result.targetX;
                targetCy=result.targetY;
                subjectKind=result.kind;
                guide.setGuide(result.box,result.tip,false);
                Bitmap low=previewBitmap(240,Math.max(160,Math.round(240f*texture.getHeight()/Math.max(1,texture.getWidth()))));
                tracker=new SubjectTracker();
                if(low!=null){tracker.init(low,result.box);low.recycle();}
                setAiButtonState();
                ui.removeCallbacks(trackLoop);
                ui.postDelayed(trackLoop,180);
            });
        });
    }

    private Bitmap previewBitmap(int w,int h){
        Bitmap src=texture==null?null:texture.getBitmap();
        if(src==null)return null;
        Bitmap out=Bitmap.createScaledBitmap(src,w,h,true);
        if(out!=src)src.recycle();
        return out;
    }

    private Bitmap resize(Bitmap b,int max){int w=b.getWidth(),h=b.getHeight();if(Math.max(w,h)<=max)return b;float f=max/(float)Math.max(w,h);return Bitmap.createScaledBitmap(b,Math.round(w*f),Math.round(h*f),true);}
    private void stopAi(){aiActive=false;aiBusy=false;tracker=null;centeredSince=0;targetCx=.5f;targetCy=.5f;subjectKind="";ui.removeCallbacks(trackLoop);if(guide!=null)guide.clearGuide();setAiButtonState();}
    private void setAiButtonState(){if(aiButton==null)return;aiButton.setText(aiBusy?"✦\nĐang tìm…":aiActive?"✦\nDừng AI":"✦\nAI hỗ trợ");aiButton.setTextColor((aiBusy||aiActive)?Color.BLACK:Color.WHITE);aiButton.setBackground(pill((aiBusy||aiActive)?GOLD:Color.TRANSPARENT,dp(28),0));}
    private boolean isCentered(float[] b){float cx=b[0]+b[2]/2f,cy=b[1]+b[3]/2f;return Math.abs(cx-targetCx)<.055f&&Math.abs(cy-targetCy)<.065f;}
    private String localDirection(float[] b,boolean ready){
        if(ready)return (subjectKind.isEmpty()?"Bố cục":subjectKind)+" đạt ✓ • giữ máy";
        float cx=b[0]+b[2]/2f,cy=b[1]+b[3]/2f;
        if(cx<targetCx-.06f)return"Dịch máy sang trái";
        if(cx>targetCx+.06f)return"Dịch máy sang phải";
        if(cy<targetCy-.07f)return"Nâng máy lên một chút";
        if(cy>targetCy+.07f)return"Hạ máy xuống một chút";
        return"Giữ máy ổn định…";
    }

'''
s = s[:start] + new_ai + s[end:]

start = s.index('    private void settings()')
end = s.index('    private void openGallery()')
new_settings = r'''    private void settings(){
        ScrollView sc=new ScrollView(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22),dp(12),dp(22),dp(8));
        sc.addView(box);

        TextView h=text("AI căn chỉnh cục bộ",18,Color.BLACK,Gravity.LEFT);
        h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(h,new LinearLayout.LayoutParams(-1,dp(46)));

        TextView note=text("V1.3 không dùng Gemini để chọn chủ thể. App tự tìm khuôn mặt hoặc vùng nổi bật trên máy, sau đó tracker local bám khung realtime. Không cần API key và không gửi frame lên cloud.",12,Color.DKGRAY,Gravity.LEFT);
        note.setPadding(0,dp(4),0,dp(16));
        box.addView(note);

        TextView camTitle=text("Camera HAL / SAT",16,Color.BLACK,Gravity.LEFT);
        camTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(camTitle);
        TextView diag=text(camera==null?"Chưa khởi tạo camera":camera.diagnostics(),11,Color.DKGRAY,Gravity.LEFT);
        diag.setTypeface(Typeface.MONOSPACE);
        diag.setTextIsSelectable(true);
        diag.setPadding(0,dp(8),0,dp(20));
        box.addView(diag);

        new AlertDialog.Builder(this).setTitle("Tùy chỉnh").setView(sc).setPositiveButton("Đóng",null).show();
    }

    private void help(){
        new AlertDialog.Builder(this)
                .setTitle("AI Camera • Local V1.3")
                .setMessage("AI hỗ trợ hiện chạy hoàn toàn trên máy:\n\n• Ưu tiên khuôn mặt/chân dung.\n• Nếu không có mặt, dùng saliency để tìm vùng nổi bật.\n• Sau lần khóa đầu, tracker local bám chủ thể liên tục.\n• Composition engine tự đưa hướng dẫn dịch máy.\n\nGemini không còn quyết định subjectBox.")
                .setPositiveButton("OK",null).show();
    }

    private void profile(){
        new AlertDialog.Builder(this)
                .setTitle("Của tôi")
                .setMessage("Không cần đăng nhập • Không hội viên\nAI căn chỉnh: Local / không API\nBộ lọc: "+selectedFilter+"\n\n"+(camera==null?"":camera.diagnostics()))
                .setNegativeButton("Đóng",null)
                .setPositiveButton("Tùy chỉnh",(d,w)->settings()).show();
    }

'''
s = s[:start] + new_settings + s[end:]

p.write_text(s, encoding='utf-8')
