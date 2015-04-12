package com.mamlambo.mirror;

import java.io.File;
import java.io.FileOutputStream;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class MirrorActivity extends Activity implements PictureCallback {
	private final static String	DEBUG_TAG		= "MirrorActivity";
	private Camera				mCam;
	private MirrorView			mCamPreview;
	private int					mCameraId		= 0;
	private FrameLayout			mPreviewLayout;
	private ViewFlipper			mViewFlipper	= null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mViewFlipper = (ViewFlipper) findViewById(R.id.myViewflipper);

		// do we have a camera?
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			Toast.makeText(this, "No camera feature on this device", Toast.LENGTH_LONG).show();
		} else {

			mCameraId = findFirstFrontFacingCamera();

			if (mCameraId >= 0) {
				mPreviewLayout = (FrameLayout) findViewById(R.id.camPreview);
				mPreviewLayout.removeAllViews();

				startCameraInLayout(mPreviewLayout, mCameraId);

				Button takePic = (Button) findViewById(R.id.capture);
				takePic.setOnClickListener(new View.OnClickListener() {

					public void onClick(View v) {
						mCam.takePicture(null, null, MirrorActivity.this);

					}
				});
			} else {
				Toast.makeText(this, "No front facing camera found.", Toast.LENGTH_LONG).show();
			}
		}
	}

	private int findFirstFrontFacingCamera() {
		int foundId = -1;
		// find the first front facing camera
		int numCams = Camera.getNumberOfCameras();
		for (int camId = 0; camId < numCams; camId++) {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(camId, info);
			if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
				Log.d(DEBUG_TAG, "Found front facing camera");
				foundId = camId;
				break;
			}
		}
		return foundId;
	}

	private void startCameraInLayout(FrameLayout layout, int cameraId) {

		// TODO pull this out of the UI thread.
		mCam = Camera.open(cameraId);
		if (mCam != null) {
			mCamPreview = new MirrorView(this, mCam);
			layout.addView(mCamPreview);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mCam == null && mPreviewLayout != null) {
			mPreviewLayout.removeAllViews();
			startCameraInLayout(mPreviewLayout, mCameraId);
		}
	}

	@Override
	protected void onPause() {
		if (mCam != null) {
			mCam.release();
			mCam = null;
		}
		super.onPause();
	}

	private File	pictureFileDir;

	public void onPictureTaken(byte[] data, Camera camera) {
		pictureFileDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "DRC");
		// File pictureFileDir = new
		// File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
		// "SimpleSelfCam");
		if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {

			Log.d(DEBUG_TAG, "Can't create directory to save image");
			Toast.makeText(this, "Can't make path to save pic.", Toast.LENGTH_LONG).show();
			return;

		}

		String filename = pictureFileDir.getPath() + File.separator + "latest_mug.jpg";
		File pictureFile = new File(filename);

		try {
			fos = new FileOutputStream(pictureFile);
			// FileOutputStream fos = new FileOutputStream(pictureFile);
			fos.write(data);
			fos.close();
			Toast.makeText(this, "Image saved as latest_mug.jpg", Toast.LENGTH_LONG).show();
		} catch (Exception error) {
			Log.d(DEBUG_TAG, "File not saved: " + error.getMessage());
			Toast.makeText(this, "Can't save image.", Toast.LENGTH_LONG).show();
		}
	}

	private FileOutputStream	fos;

	@Override
	public boolean onTouchEvent(MotionEvent touchevent) {
		super.onTouchEvent(touchevent);

		float lastX = 0;
		switch (touchevent.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				lastX = touchevent.getX();
				break;
			}
			case MotionEvent.ACTION_UP: {
				Toast.makeText(MirrorActivity.this, "touch", Toast.LENGTH_SHORT).show();
				float currentX = touchevent.getX();
				mViewFlipper.setDisplayedChild(1);
				// if (lastX > currentX) {
				// mViewFlipper.setDisplayedChild(1);
				// } else {
				// mViewFlipper.setDisplayedChild(0);
				// }
			}

		}
		return false;
	}

	public class MirrorView extends SurfaceView implements SurfaceHolder.Callback {
		private SurfaceHolder	mHolder;
		private Camera			mCamera;

		public MirrorView(Context context, Camera camera) {
			super(context);
			mCamera = camera;
			mHolder = getHolder();
			mHolder.addCallback(this);
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		public void surfaceCreated(SurfaceHolder holder) {
			try {
				mCamera.setPreviewDisplay(holder);
				mCamera.startPreview();
			} catch (Exception error) {
				Log.d(DEBUG_TAG, "Error starting mPreviewLayout: " + error.getMessage());
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
			if (mHolder.getSurface() == null) { return; }

			// can't make changes while mPreviewLayout is active
			try {
				mCamera.stopPreview();
			} catch (Exception e) {

			}

			try {
				// set rotation to match device orientation
				setCameraDisplayOrientationAndSize();

				// start up the mPreviewLayout
				mCamera.setPreviewDisplay(mHolder);
				mCamera.startPreview();

			} catch (Exception error) {
				Log.d(DEBUG_TAG, "Error starting mPreviewLayout: " + error.getMessage());
			}
		}

		public void setCameraDisplayOrientationAndSize() {
			CameraInfo info = new CameraInfo();
			Camera.getCameraInfo(mCameraId, info);
			int rotation = getWindowManager().getDefaultDisplay().getRotation();
			int degrees = rotation * 90;

			/*
			 * // the above is just a shorter way of doing this, but could break
			 * if the values change switch (rotation) { case Surface.ROTATION_0:
			 * degrees = 0; break; case Surface.ROTATION_90: degrees = 90;
			 * break; case Surface.ROTATION_180: degrees = 180; break; case
			 * Surface.ROTATION_270: degrees = 270; break; }
			 */

			int result;
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				result = (info.orientation + degrees) % 360;
				result = (360 - result) % 360;
			} else {
				result = (info.orientation - degrees + 360) % 360;
			}
			mCamera.setDisplayOrientation(result);

			Camera.Size previewSize = mCam.getParameters().getPreviewSize();
			if (result == 90 || result == 270) {
				// swap - the physical camera itself doesn't rotate in relation
				// to the screen ;)
				mHolder.setFixedSize(previewSize.height, previewSize.width);
			} else {
				mHolder.setFixedSize(previewSize.width, previewSize.height);

			}
		}

		private void MakeImage() {
			Bitmap bitmap_frame = null;
			Bitmap bitmap_capture = null;
			Bitmap bitmap_newimage = null;
			bitmap_frame = BitmapFactory.decodeResource(getResources(), R.drawable.frm1);
			bitmap_capture = BitmapFactory.decodeFile(pictureFileDir.getPath() + File.separator + "latest_mug.jpg");

			Config config = bitmap_frame.getConfig();
			if (config == null) {
				config = Bitmap.Config.ARGB_8888;
			}

			bitmap_newimage = Bitmap.createBitmap(bitmap_frame.getWidth(), bitmap_frame.getHeight(), config);
			Canvas canvas = new Canvas(bitmap_newimage);

			canvas.drawBitmap(bitmap_newimage, 0, 0, null);

			Paint paint = new Paint();

			PorterDuff.Mode selectedmode = Mode.DST_ATOP;

			paint.setXfermode(new PorterDuffXfermode(selectedmode));
			canvas.drawBitmap(bitmap_capture, 0, 0, paint);
		}

	}
}