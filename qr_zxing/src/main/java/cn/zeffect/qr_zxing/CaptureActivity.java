package cn.zeffect.qr_zxing;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

import cn.zeffect.qr_zxing.camera.CameraManager;
import cn.zeffect.qr_zxing.camera.PreviewFrameShotListener;
import cn.zeffect.qr_zxing.camera.Size;
import cn.zeffect.qr_zxing.decode.DecodeListener;
import cn.zeffect.qr_zxing.decode.DecodeThread;
import cn.zeffect.qr_zxing.decode.LuminanceSource;
import cn.zeffect.qr_zxing.decode.PlanarYUVLuminanceSource;
import cn.zeffect.qr_zxing.decode.RGBLuminanceSource;
import cn.zeffect.qr_zxing.util.DocumentUtil;
import cn.zeffect.qr_zxing.view.CaptureView;

public class CaptureActivity extends Activity implements SurfaceHolder.Callback, PreviewFrameShotListener, DecodeListener,
        OnCheckedChangeListener {

    private static final long VIBRATE_DURATION = 200L;
    private static final int REQUEST_CODE_ALBUM = 0;
    public static final String RESULT_DATA = "result";
    public static final String EXTRA_BITMAP = "bitmap";

    private SurfaceView previewSv;
    private CaptureView captureView;
    private CameraManager mCameraManager;
    private DecodeThread mDecodeThread;
    private Rect previewFrameRect = null;
    private boolean isDecoding = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qr_zxing_activity_capture);
        previewSv = (SurfaceView) findViewById(R.id.sv_preview);
        captureView = (CaptureView) findViewById(R.id.cv_capture);
        captureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mCameraManager.focusOnTouch(event);
                return true;
            }
        });
        previewSv.getHolder().addCallback(this);
        mCameraManager = new CameraManager(this);
        mCameraManager.setPreviewFrameShotListener(this);
        //

        //
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCameraManager.initCamera(holder);
        if (!mCameraManager.isCameraAvailable()) {
            Toast.makeText(CaptureActivity.this, R.string.qr_zxing_capture_camera_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (mCameraManager.isFlashlightAvailable()) {
        }
        mCameraManager.startPreview();
        if (!isDecoding) {
            mCameraManager.requestPreviewFrameShot();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCameraManager.stopPreview();
        if (mDecodeThread != null) {
            mDecodeThread.cancel();
        }
        mCameraManager.release();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPreviewFrame(byte[] data, Size dataSize) {
        if (mDecodeThread != null) {
            mDecodeThread.cancel();
        }
        if (previewFrameRect == null) {
            previewFrameRect = mCameraManager.getPreviewFrameRect(captureView.getFrameRect());
        }
        PlanarYUVLuminanceSource luminanceSource = new PlanarYUVLuminanceSource(data, dataSize, previewFrameRect);
        mDecodeThread = new DecodeThread(luminanceSource, CaptureActivity.this);
        isDecoding = true;
        mDecodeThread.execute();
    }

    @Override
    public void onDecodeSuccess(Result result, LuminanceSource source, Bitmap bitmap) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(VIBRATE_DURATION);
        isDecoding = false;
//        if (bitmap.getWidth() > 100 || bitmap.getHeight() > 100) {
//            Matrix matrix = new Matrix();
//            matrix.postScale(100f / bitmap.getWidth(), 100f / bitmap.getHeight());
//            Bitmap resizeBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
//            bitmap.recycle();
//            bitmap = resizeBmp;
//        }
        Intent resultData = new Intent();
        resultData.putExtra(RESULT_DATA, result.getText());
//        resultData.putExtra(EXTRA_BITMAP, bitmap);
        setResult(RESULT_OK, resultData);
        finish();
    }

    @Override
    public void onDecodeFailed(LuminanceSource source) {
        if (source instanceof RGBLuminanceSource) {
            Toast.makeText(CaptureActivity.this, R.string.qr_zxing_invalid_qr_code, Toast.LENGTH_SHORT).show();
        }
        isDecoding = false;
        mCameraManager.requestPreviewFrameShot();
    }

    @Override
    public void foundPossibleResultPoint(ResultPoint point) {
        captureView.addPossibleResultPoint(point);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            mCameraManager.enableFlashlight();
        } else {
            mCameraManager.disableFlashlight();
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ALBUM && resultCode == RESULT_OK && data != null) {
            Bitmap cameraBitmap = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                String path = DocumentUtil.getPath(CaptureActivity.this, data.getData());
                cameraBitmap = DocumentUtil.getBitmap(path);
            } else {
                // Not supported in SDK lower that KitKat
            }
            if (cameraBitmap != null) {
                if (mDecodeThread != null) {
                    mDecodeThread.cancel();
                }
                int width = cameraBitmap.getWidth();
                int height = cameraBitmap.getHeight();
                int[] pixels = new int[width * height];
                cameraBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                RGBLuminanceSource luminanceSource = new RGBLuminanceSource(pixels, new Size(width, height));
                mDecodeThread = new DecodeThread(luminanceSource, CaptureActivity.this);
                isDecoding = true;
                mDecodeThread.execute();
            }
        }
    }

}
