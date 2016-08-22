package cn.zeffect.qr_zxing.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Handler;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CameraManager implements Camera.AutoFocusCallback, Camera.PreviewCallback {

    private enum CameraState {
        CLOSED, OPEN, PREVIEW;
    }

    private Camera mCamera;
    private Size screenSize;
    private Size cameraSize;
    private CameraState mState;
    private PreviewFrameShotListener mFrameShotListener;

    private static final int REQUEST_AUTO_FOCUS_INTERVAL_MS = 1500;
    private static final int MESSAGE_REQUEST_AUTO_FOCUS = 0;

    private Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MESSAGE_REQUEST_AUTO_FOCUS:
                    if (mState == CameraState.PREVIEW && mCamera != null) {
                        mCamera.autoFocus(CameraManager.this);
                    }
                    break;

                default:
                    break;
            }
        }

        ;
    };

    @SuppressWarnings("deprecation")
    public CameraManager(Context context) {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        screenSize = new Size(display.getWidth(), display.getHeight());
        mState = CameraState.CLOSED;
    }

    public boolean initCamera(SurfaceHolder holder) {
        mCamera = Camera.open();
        if (mCamera == null) {
            return false;
        }
        mState = CameraState.OPEN;
        mCamera.setDisplayOrientation(90);
        Camera.Parameters parameters = mCamera.getParameters();
        cameraSize = getBestPreviewSize(parameters, screenSize);
        parameters.setPreviewSize(cameraSize.height, cameraSize.width);
        parameters.setPreviewFormat(ImageFormat.NV21);//Default
        mCamera.setParameters(parameters);
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public boolean isCameraAvailable() {
        return mCamera == null ? false : true;
    }

    public boolean isFlashlightAvailable() {
        if (mCamera == null) {
            return false;
        }
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> flashModes = parameters.getSupportedFlashModes();
        if (flashModes == null || flashModes.size() < 1) {
            return false;
        }
        boolean isFlashOnAvailable = false;
        boolean isFlashOffAvailable = false;
        for (String flashMode : flashModes) {
            if (Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                isFlashOnAvailable = true;
            }
            if (Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                isFlashOffAvailable = true;
            }
            if (isFlashOnAvailable && isFlashOffAvailable) {
                return true;
            }
        }
        return false;
    }

    public void enableFlashlight() {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        mCamera.setParameters(parameters);
    }

    public void disableFlashlight() {
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        mCamera.setParameters(parameters);
    }

    public void startPreview() {
        if (mCamera != null) {
            mState = CameraState.PREVIEW;
            mCamera.startPreview();
            mCamera.autoFocus(CameraManager.this);
        }
    }

    public void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mState = CameraState.OPEN;
        }
    }

    public void release() {
        if (mCamera != null) {
            mCamera.setOneShotPreviewCallback(null);
            mCamera.release();
            mState = CameraState.CLOSED;
        }
    }

    public void requestPreviewFrameShot() {
        mCamera.setOneShotPreviewCallback(CameraManager.this);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (mFrameShotListener != null) {
            data = rotateYUVdata90(data);
            mFrameShotListener.onPreviewFrame(data, cameraSize);
        }
    }

    /**
     * 获取与屏幕大小最相近的预览图像大小
     */
    private Size getBestPreviewSize(Camera.Parameters parameters, Size screenSize) {
        Size size = new Size(screenSize);
        int diff = Integer.MAX_VALUE;
        List<Camera.Size> previewList = parameters.getSupportedPreviewSizes();
        for (Camera.Size previewSize : previewList) {
            // Rotate 90 degrees
            int previewWidth = previewSize.height;
            int previewHeight = previewSize.width;
            int newDiff = Math.abs(previewWidth - screenSize.width) * Math.abs(previewWidth - screenSize.width)
                    + Math.abs(previewHeight - screenSize.height) * Math.abs(previewHeight - screenSize.height);
            if (newDiff == 0) {
                size.width = previewWidth;
                size.height = previewHeight;
                return size;
            } else if (newDiff < diff) {
                diff = newDiff;
                size.width = previewWidth;
                size.height = previewHeight;
            }
        }
        return size;
    }

    /**
     * 因为预览图像和屏幕大小可能不一样，所以屏幕上的区域要根据比例转化为预览图像上对应的区域
     */
    public Rect getPreviewFrameRect(Rect screenFrameRect) {
        if (mCamera == null) {
            throw new IllegalStateException("Need call initCamera() before this.");
        }
        Rect previewRect = new Rect();
        previewRect.left = screenFrameRect.left * cameraSize.width / screenSize.width;
        previewRect.right = screenFrameRect.right * cameraSize.width / screenSize.width;
        previewRect.top = screenFrameRect.top * cameraSize.height / screenSize.height;
        previewRect.bottom = screenFrameRect.bottom * cameraSize.height / screenSize.height;
        return previewRect;
    }

    private byte[] rotateYUVdata90(byte[] srcData) {
        byte[] desData = new byte[srcData.length];
        int srcWidth = cameraSize.height;
        int srcHeight = cameraSize.width;

        // Only copy Y
        int i = 0;
        for (int x = 0; x < srcWidth; x++) {
            for (int y = srcHeight - 1; y >= 0; y--) {
                desData[i++] = srcData[y * srcWidth + x];
            }
        }

        return desData;
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        if (mState == CameraState.PREVIEW) {
            mHandler.sendEmptyMessageDelayed(MESSAGE_REQUEST_AUTO_FOCUS, REQUEST_AUTO_FOCUS_INTERVAL_MS);
        }
    }

    public void setPreviewFrameShotListener(PreviewFrameShotListener l) {
        mFrameShotListener = l;
    }


    public void focusOnTouch(MotionEvent event) {
        Rect focusRect = calculateTapArea(event.getRawX(), event.getRawY(), 1f);
        Rect meteringRect = calculateTapArea(event.getRawX(), event.getRawY(), 1.5f);

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

        if (parameters.getMaxNumFocusAreas() > 0) {
            List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
            focusAreas.add(new Camera.Area(focusRect, 1000));

            parameters.setFocusAreas(focusAreas);
        }

        if (parameters.getMaxNumMeteringAreas() > 0) {
            List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
            meteringAreas.add(new Camera.Area(meteringRect, 1000));

            parameters.setMeteringAreas(meteringAreas);
        }

        mCamera.setParameters(parameters);
        mCamera.autoFocus(this);
    }
    /**
     * Convert touch position x:y to {@link Camera.Area} position -1000:-1000 to 1000:1000.
     */
    private Rect calculateTapArea(float x, float y, float coefficient) {
        float focusAreaSize = 300;
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int centerX = (int) (x / getResolution().width - 1000);
        int centerY = (int) (y / getResolution().height - 1000);

        int left = clamp(centerX - areaSize / 2, -1000, 1000);
        int top = clamp(centerY - areaSize / 2, -1000, 1000);

        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);

        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }
    public List<Camera.Size> getResolutionList() {
        return  mCamera.getParameters().getSupportedPreviewSizes();
    }

    public Camera.Size getResolution() {
        Camera.Parameters params = mCamera.getParameters();
        Camera.Size s = params.getPreviewSize();
        return s;
    }
}
