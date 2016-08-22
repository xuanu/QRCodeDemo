package cn.zeffect.qr_zxing.camera;

public interface PreviewFrameShotListener {
	public void onPreviewFrame(byte[] data, Size frameSize);
}
