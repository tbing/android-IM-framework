package com.im.framework.app.ui;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.im.framework.app.R;
import com.im.framework.app.service.CMessage.MessageItem;
import com.im.framework.app.service.CMessage.MessageType;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.Shape;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * this is used for test some function in MessageActivity, and the ui mode is just a demo.
 *
 */

public class MessageActivity_Demo extends MessageActivity {
  private static final int BASE_DENSITY = 240;
  private int mDensity;

  private final String tag = "ICE";
  private AtomicInteger sendId = new AtomicInteger();

  private RelativeLayout messagePanel;
  private ScrollView sv;
  private EditText et;
  private Button button;
  private Button add;
  private Button speak;

  MediaRecorder mRecorder = null;
  MediaPlayer mPlayer = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DisplayMetrics metrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(metrics);
    this.mDensity = metrics.densityDpi;
    setContentView(R.layout.messageactivity_layout);
    getActionBar().setTitle(tag);
    registerViewObserver(new Viewer(this));
    this.sv = (ScrollView) findViewById(R.id.scroller);
    this.messagePanel = (RelativeLayout) findViewById(R.id.messagepanel);
    this.et = (EditText) findViewById(R.id.text);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(et.getLayoutParams());
    lp.width = sizeOf(lp.width);
    et.setLayoutParams(lp);
    this.button = (Button) findViewById(R.id.button);
    this.add = (Button) findViewById(R.id.add);
    this.speak = (Button) findViewById(R.id.speak);
    speak.setPadding(sizeOf(40), speak.getPaddingTop(), sizeOf(40), speak.getPaddingBottom());
    ShapeDrawable sd = new ShapeDrawable(new SpeakShape(speak, Color.DKGRAY));
    speak.setBackground(sd);
    View.OnClickListener l = new ClickListener();
    button.setOnClickListener(l);
    add.setOnClickListener(l);
    add.setLongClickable(true);
    add.setOnLongClickListener(new LongClick());
    speak.setOnTouchListener(new TouchListener());
  }

  private int sizeOf(float size) {
    float value = size * BASE_DENSITY / mDensity;
    return Math.round(value);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == -1) {
      Uri uri = data.getData();
      String us = uri.toString();
      if (us.startsWith("file://")) {
        us = us.substring(7);
      } else if (us.startsWith("content://")) {
        String[] path = {MediaStore.MediaColumns.DATA};
        Cursor c = MediaStore.Images.Media.query(getContentResolver(), uri, path);
        if (c.moveToFirst()) {
          us = c.getString(c.getColumnIndex(path[0]));
        }
      }
      sendMessage(tag, sendId.getAndIncrement(), MessageType.IMAGE.ordinal(), us.getBytes());
      ((Viewer) viewObserver).showImageView(us, false);
    }
  }

  private class TouchListener implements View.OnTouchListener {

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      if (v.getId() == R.id.speak) {
        Button button = (Button) v;
        if (event.getAction() == MotionEvent.ACTION_DOWN && button.getText().equals("Please Speak Now")) {
          String path = startRecord();
          if (path != null) {
            button.setText("Speaking");
            ShapeDrawable sd = new ShapeDrawable(new SpeakShape(button, Color.CYAN));
            button.setBackground(sd);
            button.setTag(path);
          }
        }
        if (event.getAction() == MotionEvent.ACTION_UP && button.getText().equals("Speaking")) {
          stopRecord();
          button.setText("Please Speak Now");
          ShapeDrawable sd = new ShapeDrawable(new SpeakShape(button, Color.DKGRAY));
          button.setBackground(sd);
          String path = (String) button.getTag();
          if (path != null) {
            sendMessage(tag, sendId.getAndIncrement(), MessageType.AUDIO.ordinal(), path.getBytes());
            ((Viewer) viewObserver).showAudioView(-1, path, false);
          }
        }
        return true;
      }
      return false;
    }

  }

  private class SpeakShape extends Shape {
    Button speak;
    int color;

    SpeakShape(Button speak, int color) {
      this.speak = speak;
      this.color = color;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
      int w = speak.getMeasuredWidth();
      int h = speak.getMeasuredHeight();
      int p = Math.min(speak.getPaddingLeft(), speak.getPaddingRight());
      Path path = new Path();
      path.moveTo(p, 0);
      path.lineTo(w - p, 0);
      RectF rf = new RectF(w - p, 0, w, h);
      path.arcTo(rf, 270, 180);
      path.lineTo(p, h);
      rf = new RectF(0, 0, p, h);
      path.arcTo(rf, 90, 180);
      paint.setColor(this.color);
      paint.setStyle(Style.FILL_AND_STROKE);
      canvas.drawPath(path, paint);
    }

  }

  private void stopRecord() {
    if (mRecorder == null) return;
    mRecorder.stop();
    mRecorder.release();
    mRecorder = null;
  }

  private void stopPlay() {
    if (mPlayer == null) return;
    mPlayer.stop();
    mPlayer.release();
    mPlayer = null;
  }

  private void stopMedia() {
    stopRecord();
    stopPlay();
  }

  private File createAudioFile(String format) throws IOException {
    File sd = Environment.getExternalStorageDirectory();
    File home = new File(sd, "IM");
    if (!home.exists()) {
      home.mkdir();
    }
    if (home.isDirectory()) {
      File dir = new File(home, "AUDIO");
      if (!dir.exists()) {
        dir.mkdir();
      }
      String name = String.valueOf(System.currentTimeMillis());
      name = name.substring(name.length() - 10) + "." + format;
      File sample = new File(dir, name);
      sample.createNewFile();
      return sample;
    }
    return null;
  }

  private String startRecord() {
    stopMedia();

    File sampleFile;
    try {
      sampleFile = createAudioFile("3gpp");
    } catch (IOException ioe) {
      return null;
    }
    if (sampleFile != null) {
      mRecorder = new MediaRecorder();
      mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
      mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
      mRecorder.setOutputFile(sampleFile.getAbsolutePath());

      // Handle IOException
      try {
        mRecorder.prepare();
      } catch (IOException exception) {
        mRecorder.reset();
        mRecorder.release();
        mRecorder = null;
        sampleFile.delete();
        return null;
      }
      // Handle RuntimeException if the recording couldn't start
      try {
        mRecorder.start();
      } catch (RuntimeException exception) {
        AudioManager audioMngr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        boolean busy = ((audioMngr.getMode() == AudioManager.MODE_IN_CALL) || (audioMngr.getMode() == AudioManager.MODE_IN_COMMUNICATION));
        if (busy) {
          Toast.makeText(this, "telephone is in use, please wait...", Toast.LENGTH_SHORT).show();
        }
        mRecorder.reset();
        mRecorder.release();
        mRecorder = null;
        sampleFile.delete();
        return null;
      }
      return sampleFile.getAbsolutePath();
    }
    return null;
  }

  private class PlayCompletion implements MediaPlayer.OnCompletionListener {
    AudioButton play;

    PlayCompletion(AudioButton play) {
      this.play = play;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
      stopMedia();
      play.stopRotate();
    }
  }

  private class PlayError implements MediaPlayer.OnErrorListener {
    AudioButton play;

    PlayError(AudioButton play) {
      this.play = play;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
      stopMedia();
      play.stopRotate();
      return true;
    }
  }

  private void play(String path, AudioButton play) {
    stopMedia();

    mPlayer = new MediaPlayer();
    try {
      mPlayer.setDataSource(path);
      mPlayer.setOnCompletionListener(new PlayCompletion(play));
      mPlayer.setOnErrorListener(new PlayError(play));
      mPlayer.prepare();
      mPlayer.start();
    } catch (IllegalArgumentException e) {
      mPlayer = null;
      return;
    } catch (IOException e) {
      mPlayer = null;
      return;
    }
  }

  private class LongClick implements View.OnLongClickListener {

    @Override
    public boolean onLongClick(View v) {
      if (v.getId() == R.id.add) {
        if (speak.getVisibility() == View.GONE) {
          et.setVisibility(View.GONE);
          button.setVisibility(View.GONE);
          speak.setVisibility(View.VISIBLE);
        } else if (speak.getVisibility() == View.VISIBLE) {
          speak.setVisibility(View.GONE);
          et.setVisibility(View.VISIBLE);
          button.setVisibility(View.VISIBLE);
        }
        return true;
      }
      return false;
    }

  }

  private class ClickListener implements View.OnClickListener {

    private String path;

    ClickListener() {}

    ClickListener(String path) {
      this.path = path;
    }

    @Override
    public void onClick(View v) {
      if (v.getId() == R.id.button) {
        String message = et.getText().toString();
        String contents;
        try {
          contents = new String(message.getBytes(), "ISO-8859-1");
        } catch (Exception e) {
          contents = message;
        }
        sendMessage(tag, sendId.getAndIncrement(), MessageType.TEXT.ordinal(), contents.getBytes());
        ((Viewer) viewObserver).showTextView(message, false);
      } else if (v.getId() == R.id.add) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setType("image/*");
        MessageActivity_Demo.this.startActivityForResult(intent, 0);
      } else {
        AudioButton ab = (AudioButton) v;
        ab.setEnabled(false);
        play(this.path, ab);
        ab.startRotate();
      }
    }
  }

  private class AudioButton extends Button {

    private boolean rotating = false;

    AudioButton(Context context) {
      super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      this.setMeasuredDimension(this.getMeasuredWidth(), this.getMeasuredWidth());
    }

    public void startRotate() {
      this.rotating = true;

      new Thread() {
        float rotation = 10;
        float orig = AudioButton.this.getRotation();

        public void run() {
          long last = System.currentTimeMillis();
          while (rotating) {
            long now = System.currentTimeMillis();
            if (now - last > 100) {
              last = now;
              getMessageHandler().post(new Runnable() {
                public void run() {
                  AudioButton.this.setRotation(rotation);
                }
              });
              Log.i("play", "rotate");
              rotation += 10;
              if (rotation >= 360) {
                rotation = 0;
              }
            }
          }
          getMessageHandler().post(new Runnable() {
            public void run() {
              AudioButton.this.setRotation(orig);
            }
          });
          synchronized (AudioButton.this) {
            AudioButton.this.notifyAll();
          }
        }
      }.start();
    }

    public synchronized void stopRotate() {
      this.rotating = false;
      try {
        this.wait(1000);
      } catch (InterruptedException ie) {}
      this.setEnabled(true);
    }
  }

  private class Viewer implements ViewObserver {
    private Context context;
    private View currentView;
    private AtomicInteger viewId = new AtomicInteger(1);
    private SparseArray<String> multipartResult = new SparseArray<String>();

    Viewer(Context context) {
      this.context = context;
    }

    @Override
    public void handleTextView(Bundle data) {
      String who = data.getString(MessageItem.SOURCE);
      byte[] texts = data.getByteArray(MessageItem.CONTENTS);
      if (who.equals(tag)) {
        String ts = new String(texts);
        try {
          ts = new String(ts.getBytes("ISO-8859-1"));
        } catch (Exception e) {
          ts = new String(texts);
        }
        showTextView(ts, true);
      }
    }

    public void showTextView(String message, boolean receive) {
      TextView textView = new TextView(context);
      textView.setText(message);
      textView.setTextSize(textView.getTextSize() * (float) (1.2f * BASE_DENSITY / mDensity));
      textView.setMaxWidth(sizeOf(200));
      textView.setPadding(sizeOf(15), textView.getPaddingTop(), sizeOf(15), textView.getPaddingBottom());
      TextViewShape shape = new TextViewShape(textView, receive ? false : true);
      ShapeDrawable sd = new ShapeDrawable(shape);
      textView.setBackground(sd);
      showView(textView, receive);
    }

    class TextViewShape extends Shape {
      private TextView view;
      private boolean right;

      TextViewShape(TextView view, boolean right) {
        this.view = view;
        this.right = right;
      }

      @Override
      public void draw(Canvas canvas, Paint paint) {
        int mWidth = view.getMeasuredWidth();
        int mHeight = view.getMeasuredHeight();
        int mPadding = right ? view.getPaddingRight() : view.getPaddingLeft();
        Rect rect = new Rect();
        view.getDrawingRect(rect);
        Path path = new Path();
        if (right) {
          path.moveTo(mWidth - mPadding / 2, mHeight / 3);
          path.lineTo(mWidth, mHeight / 3 + mPadding / 2);
          path.lineTo(mWidth - mPadding / 2, mHeight / 3 + mPadding);
        } else {
          path.moveTo(mPadding / 2, mHeight / 3);
          path.lineTo(0, mHeight / 3 + mPadding / 2);
          path.lineTo(mPadding / 2, mHeight / 3 + mPadding);
        }
        float r = 10f * BASE_DENSITY / mDensity;
        float[] radis = new float[8];
        for (int i = 0; i < radis.length; i++) {
          radis[i] = r;
        }
        RectF rectf = null;
        if (right) {
          rectf = new RectF(rect.left, rect.top, rect.right - mPadding / 2, rect.bottom);
        } else {
          rectf = new RectF(rect.left + mPadding / 2, rect.top, rect.right, rect.bottom);
        }
        path.addRoundRect(rectf, radis, right ? Path.Direction.CW : Path.Direction.CCW);
        paint.setStyle(Style.FILL_AND_STROKE);
        paint.setColor(right ? Color.GREEN : Color.MAGENTA);
        canvas.drawPath(path, paint);
      }

    }

    private RelativeLayout.LayoutParams getLayoutParams() {
      return new RelativeLayout.LayoutParams(MarginLayoutParams.WRAP_CONTENT, MarginLayoutParams.WRAP_CONTENT);
    }

    private void showView(View view, boolean receive) {
      RelativeLayout.LayoutParams lp = getLayoutParams();
      lp.setMargins(sizeOf(20), sizeOf(20), sizeOf(20), sizeOf(20));
      int baseRule = receive ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT;
      lp.addRule(baseRule);
      if (currentView != null) {
        lp.addRule(RelativeLayout.BELOW, currentView.getId());
      }
      view.setId(viewId.getAndIncrement());
      messagePanel.addView(view, lp);
      messagePanel.requestLayout();
      messagePanel.invalidate();
      currentView = view;
      currentView.setFocusable(true);
      sv.fullScroll(ScrollView.FOCUS_DOWN);
    }

    public void showImageView(String path, boolean receive) {
      Bitmap bm = BitmapFactory.decodeFile(path);
      bm = Bitmap.createScaledBitmap(bm, sizeOf(200), sizeOf(300), true);
      ImageView imageView = new ImageView(context);
      imageView.setImageBitmap(bm);
      imageView.setAdjustViewBounds(true);
      imageView.setMaxHeight(sizeOf(300));
      imageView.setMaxWidth(sizeOf(200));
      showView(imageView, receive);
    }

    @Override
    public void handleImageView(Bundle data, int id) {
      String who = data.getString(MessageItem.SOURCE);
      byte[] paths = data.getByteArray(MessageItem.CONTENTS);
      if (who != null && paths != null) {
        if (who.equals(tag)) {
          multipartResult.append(id, new String(paths));
        }
      } else {
        String value = multipartResult.get(id);
        if (value != null) {
          multipartResult.delete(id);
          showImageView(value, true);
        }
      }
    }

    public void showAudioView(int id, String path, boolean receive) {
      Button play = new AudioButton(context);
      play.setTag(id);
      play.setEnabled(receive ? false : true);
      play.setText("Play");
      play.setTextSize(play.getTextSize() * (float) (1.2f * BASE_DENSITY / mDensity));
      ShapeDrawable sd = new ShapeDrawable(new OvalShape());
      Paint paint = sd.getPaint();
      paint.setColor(Color.YELLOW);
      paint.setStyle(Style.FILL_AND_STROKE);
      play.setBackground(sd);
      if (!receive) {
        play.setOnClickListener(new ClickListener(path));
      }
      showView(play, receive);
    }

    @Override
    public void handleAudioView(Bundle data, int id) {
      String who = data.getString(MessageItem.SOURCE);
      byte[] paths = data.getByteArray(MessageItem.CONTENTS);
      if (who != null && paths != null) {
        if (who.equals(tag)) {
          multipartResult.append(id, new String(paths));
          showAudioView(id, null, true);
        }
      } else {
        String value = multipartResult.get(id);
        if (value != null) {
          multipartResult.delete(id);
          if (Integer.valueOf(id).equals(currentView.getTag())) {
            Button play = (Button) currentView;
            play.setOnClickListener(new ClickListener(value));
            play.setEnabled(true);
          }
        }
      }
    }

    @Override
    public void handleVedioView(Bundle data, int id) {

    }

    @Override
    public void handleSendingStatus(int id, int status) {

    }

  }
}
