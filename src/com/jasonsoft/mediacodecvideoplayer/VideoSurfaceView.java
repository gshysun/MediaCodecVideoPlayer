package com.jasonsoft.mediacodecvideoplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Surface;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.jasonsoft.mediacodecvideoplayer.cache.CacheManager;

/**
 * Displays a video file.  The VideoSurfaceView class
 * can load images from various sources (such as resources or content
 * providers), takes care of computing its measurement from the video so that
 * it can be used in any layout manager, and provides various display options
 * such as scaling and tinting.
 */
public class VideoSurfaceView extends SurfaceView {
    private final static String TAG = VideoSurfaceView.class.getSimpleName();

    private static final long TIMEOUT_US = 10000;
    private static final long ONE_SECOND_IN_MILLIS = 1000;

    // settable by the client
    private String mPath;
    private Bitmap mBitmap = null;
    private VideoPlaybackAsyncTask mVideoPlaybackAsyncTask;

    private Matrix mMatrix;
    private Matrix mBitmapMatrix = null;

    // Avoid allocations...
    private RectF mTempSrc = new RectF();
    private RectF mTempDst = new RectF();

    // All the stuff we need for playing and showing a video
    private SurfaceHolder mSurfaceHolder = null;
    private int         mVideoWidth;
    private int         mVideoHeight;
    private int         mSurfaceWidth;
    private int         mSurfaceHeight;
    private Context mContext;

    public VideoSurfaceView(Context context) {
        super(context);
        mContext = context;
        initVideoSurfaceView();
    }

    public VideoSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        initVideoSurfaceView();
    }

    public VideoSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        initVideoSurfaceView();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.i("@@@@", "onMeasure(" + MeasureSpec.toString(widthMeasureSpec) + ", "
        //        + MeasureSpec.toString(heightMeasureSpec) + ")");

        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        if (mVideoWidth > 0 && mVideoHeight > 0) {

            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                // the size is fixed
                width = widthSpecSize;
                height = heightSpecSize;

                // for compatibility, we adjust size based on aspect ratio
                if ( mVideoWidth * height  < width * mVideoHeight ) {
                    //Log.i("@@@", "image too wide, correcting");
                    width = height * mVideoWidth / mVideoHeight;
                } else if ( mVideoWidth * height  > width * mVideoHeight ) {
                    //Log.i("@@@", "image too tall, correcting");
                    height = width * mVideoHeight / mVideoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                // only the width is fixed, adjust the height to match aspect ratio if possible
                width = widthSpecSize;
                height = width * mVideoHeight / mVideoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                // only the height is fixed, adjust the width to match aspect ratio if possible
                height = heightSpecSize;
                width = height * mVideoWidth / mVideoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    width = widthSpecSize;
                }
            } else {
                // neither the width nor the height are fixed, try to use actual video size
                width = mVideoWidth;
                height = mVideoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall, decrease both width and height
                    height = heightSpecSize;
                    width = height * mVideoWidth / mVideoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide, decrease both width and height
                    width = widthSpecSize;
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
        } else {
            // no size yet, just adopt the given spec sizes
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(VideoSurfaceView.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(VideoSurfaceView.class.getName());
    }

    private void initVideoSurfaceView() {
        mMatrix = new Matrix();
        mVideoWidth = 0;
        mVideoHeight = 0;
        mVideoWidth = 0;
        mVideoHeight = 0;
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback()
    {
        public void surfaceChanged(SurfaceHolder holder, int format,
                int w, int h)
        {
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            stopVideoPlayTask();
        }

        public void surfaceCreated(SurfaceHolder holder)
        {
            mSurfaceHolder = holder;
        }

        public void surfaceDestroyed(SurfaceHolder holder)
        {
            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;
            stopVideoPlayTask();
        }
    };

    public void setVideoPath(String path) {
        mPath = path;
    }

    public void stopPlayback() {
        stopVideoPlayTask();
    }

    private void stopVideoPlayTask() {
        if (mVideoPlaybackAsyncTask != null) {
            mVideoPlaybackAsyncTask.cancel(false);
        }
    }

    public void start() {
        mVideoPlaybackAsyncTask = new VideoPlaybackAsyncTask(mSurfaceHolder.getSurface());
        mVideoPlaybackAsyncTask.execute(mPath);
//        mVideoPlaybackThread = new VideoPlaybackThread(mSurfaceHolder.getSurface(), mPath);
//        mVideoPlaybackThread.start();
    }

    class VideoPlaybackAsyncTask extends AsyncTask<String, Void, Void> {
        private String data;
        private Surface surface;
        private MediaExtractor extractor;
        private MediaCodec codec;
        long startTimeMs;

        public VideoPlaybackAsyncTask(Surface surface) {
            this.surface = surface;
        }

        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(String... params) {
            data = params[0];
            extractor = new MediaExtractor();
            try {
                extractor.setDataSource(data);
            } catch (IOException e) {
                return null;
            }

            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; ++i) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                Log.d(TAG, "mime type:" + mime);
                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    codec = MediaCodec.createDecoderByType(mime);
                    codec.configure(format, surface, null, 0);
                    break;
                }
            }

            if (codec == null) {
                return null;
            }

            codec.start();

            ByteBuffer[] inputBuffers = codec.getInputBuffers();
            ByteBuffer[] outputBuffers = codec.getOutputBuffers();
            BufferInfo bufferInfo = new BufferInfo();

            // Playback loop
			startTimeMs = System.currentTimeMillis();
            for (;;) {
                if (isCancelled()) {
                    Log.d(TAG, "User cancelled");
                    break;
                }

                int inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize >= 0) {
                        long presentationTimeUs = extractor.getSampleTime();
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }


                // Output part
                int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                if (outputBufferIndex >= 0) {
                    ByteBuffer buffer = outputBuffers[outputBufferIndex];
                    idle(bufferInfo.presentationTimeUs);
                    // Render frame to surface
                    codec.releaseOutputBuffer(outputBufferIndex, true);
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = codec.getOutputBuffers();
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Subsequent data will conform to new format.
                    MediaFormat format = codec.getOutputFormat();
                }

                // All decoded frames have been rendered, we can stop playing now
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }

            codec.stop();
            codec.release();
            codec = null;
            extractor.release();
            extractor = null;

            return null;
        }

        private void idle(long presentationTimeUs) {
            while (presentationTimeUs / ONE_SECOND_IN_MILLIS > System.currentTimeMillis() - startTimeMs) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        @Override
        protected void onProgressUpdate(Void... params) {
        }

        @Override
        protected void onPostExecute(Void result) {
        }
    }

}
