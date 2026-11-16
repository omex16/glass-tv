package tv.dumbtv.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * The screensaver, Apple TV style: the owner's pictures fill the screen one after another, each
 * drifting slowly (a pan and a zoom) and cross-fading into the next, with a thin clock in the
 * corner. Home shows it after a few idle minutes and any key takes it away. Two pictures at most
 * are in memory, decoded at screen size in RGB 565: about 8 MB on a 1080p TV.
 *
 * Video clips (.mp4, .mkv, .webm) in the same folder take precedence, like Apple TV's aerials:
 * played muted one after another, cropped to fill the screen, each fading in and out.
 */
final class Aerial extends FrameLayout {
    static final long SHOW_MS = 14000, FADE_MS = 2200;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler ui;
    private final ExecutorService io;
    private final ImageView[] images = new ImageView[2];
    private final ObjectAnimator[] drifts = new ObjectAnimator[2];
    private final TextView time, date;
    private final Random random = new Random();
    private List<File> pictures = new ArrayList<>();
    private int index, top, generation;
    private boolean showing;
    private final TextureView video;
    private MediaPlayer player;
    private Surface surface;
    private List<File> clips = new ArrayList<>();
    private int clip;
    private final Runnable fadeOut = new Runnable() {
        @Override
        public void run() {
            video.animate().alpha(0f).setDuration(1200).start();
        }
    };

    private final Runnable next = new Runnable() {
        @Override
        public void run() {
            if (!showing) return;
            load(index + 1);
        }
    };

    Aerial(Context c, Handler ui, ExecutorService io) {
        super(c);
        this.ui = ui;
        this.io = io;
        setBackgroundColor(0xFF000000);
        for (int i = 0; i < 2; i++) {
            images[i] = new ImageView(c);
            images[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            images[i].setAlpha(0f);
            addView(images[i], new LayoutParams(MATCH, MATCH));
        }
        video = new TextureView(c);
        video.setAlpha(0f);
        video.setVisibility(View.GONE);
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                surface = new Surface(st);
                if (showing && !clips.isEmpty()) play(clip);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                fit();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                release();
                if (surface != null) surface.release();
                surface = null;
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
        addView(video, new LayoutParams(MATCH, MATCH));
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0x99000000, 0x00000000}));
        addView(shade, new LayoutParams(MATCH, Ui.dp(c, 220), Gravity.BOTTOM));

        LinearLayout corner = new LinearLayout(c);
        corner.setOrientation(LinearLayout.VERTICAL);
        time = Ui.text(c, 54, 0xF2FFFFFF, android.graphics.Typeface.create("sans-serif-thin",
                android.graphics.Typeface.NORMAL));
        date = Ui.text(c, 16, 0xB3FFFFFF, Ui.MEDIUM);
        date.setPadding(Ui.dp(c, 3), Ui.dp(c, 6), 0, 0);
        corner.addView(time);
        corner.addView(date);
        LayoutParams lp = new LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = Ui.dp(c, 56);
        lp.bottomMargin = Ui.dp(c, 44);
        addView(corner, lp);

        setVisibility(View.GONE);
        setFocusable(false);
        setKeepScreenOn(false);
    }

    boolean showing() {
        return showing;
    }

    /** Shows the pictures in a fresh random order; false when there are none to show. */
    boolean start(List<File> files) {
        List<File> stills = new ArrayList<>(), moving = new ArrayList<>();
        for (File f : files) (Config.isVideo(f) ? moving : stills).add(f);
        if (stills.isEmpty() && moving.isEmpty()) return false;
        showing = true;
        generation++;
        setAlpha(0f);
        setVisibility(View.VISIBLE);
        animate().alpha(1f).setDuration(1200).start();
        if (!moving.isEmpty()) {
            clips = moving;
            Collections.shuffle(clips, random);
            clip = 0;
            video.setVisibility(View.VISIBLE);
            if (surface != null) play(0);
            return true;
        }
        clips = new ArrayList<>();
        pictures = stills;
        Collections.shuffle(pictures, random);
        index = -1;
        load(0);
        return true;
    }

    /** Plays clip {@code i} muted; it fades out near its end and the next one fades in. */
    private void play(int i) {
        if (!showing || clips.isEmpty() || surface == null) return;
        clip = Math.floorMod(i, clips.size());
        release();
        final int gen = generation;
        MediaPlayer p = new MediaPlayer();
        player = p;
        try {
            p.setDataSource(clips.get(clip).getPath());
            p.setSurface(surface);
            p.setVolume(0f, 0f);
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (gen != generation || mp != player) return;
                    fit();
                    mp.start();
                    video.animate().alpha(1f).setDuration(1500).start();
                    int left = mp.getDuration() - 1300;
                    if (left > 0) ui.postDelayed(fadeOut, left);
                }
            });
            p.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int w, int h) {
                    fit();
                }
            });
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (gen == generation && showing) play(clip + 1);
                }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Keeper.note("screensaver clip failed: " + clips.get(clip).getName() + " " + what + "/" + extra);
                    if (gen == generation && showing && clips.size() > 1) {
                        ui.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (gen == generation) play(clip + 1);
                            }
                        }, 500);
                    }
                    return true;
                }
            });
            p.prepareAsync();
        } catch (java.io.IOException | RuntimeException e) {
            Keeper.note("screensaver clip unreadable: " + clips.get(clip).getName());
            release();
        }
    }

    /** Crops the clip to fill the screen, like the pictures' centre crop. */
    private void fit() {
        MediaPlayer p = player;
        if (p == null) return;
        int vw, vh;
        try {
            vw = p.getVideoWidth();
            vh = p.getVideoHeight();
        } catch (IllegalStateException e) {
            return;
        }
        int w = video.getWidth(), h = video.getHeight();
        if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0) return;
        float scale = Math.max((float) w / vw, (float) h / vh);
        float sx = vw * scale / w, sy = vh * scale / h;
        Matrix m = new Matrix();
        m.setScale(sx, sy, w / 2f, h / 2f);
        video.setTransform(m);
    }

    private void release() {
        ui.removeCallbacks(fadeOut);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    void stop() {
        if (!showing) return;
        showing = false;
        generation++;
        ui.removeCallbacks(next);
        release();
        video.animate().cancel();
        video.setAlpha(0f);
        animate().cancel();
        animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (showing) return;
                setVisibility(View.GONE);
                video.setVisibility(View.GONE);
                for (int i = 0; i < 2; i++) {
                    if (drifts[i] != null) drifts[i].cancel();
                    images[i].setImageDrawable(null);
                    images[i].setAlpha(0f);
                }
            }
        }).start();
    }

    void setTime(CharSequence clock, CharSequence day) {
        time.setText(clock);
        date.setText(day);
    }

    private void load(int i) {
        final int at = Math.floorMod(i, pictures.size());
        final File file = pictures.get(at);
        final int gen = generation;
        final int w = getResources().getDisplayMetrics().widthPixels;
        final int h = getResources().getDisplayMetrics().heightPixels;
        try {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = decode(file, w, h);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != generation || !showing) return;
                            index = at;
                            show(b);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException e) {
            showing = false;
        }
    }

    /** Fades {@code b} in over the picture on screen and starts its slow drift. */
    private void show(Bitmap b) {
        int fresh = 1 - top;
        final ImageView in = images[fresh], out = images[top];
        if (b == null) {
            ui.postDelayed(next, 1000);
            return;
        }
        in.setImageBitmap(b);
        drift(fresh);
        in.animate().cancel();
        out.animate().cancel();
        in.setAlpha(0f);
        // A plain cross-fade: the shade and the clock were added after both pictures, so they
        // stay on top whichever picture is fading in.
        in.animate().alpha(1f).setDuration(FADE_MS).setListener(null).start();
        out.animate().alpha(0f).setDuration(FADE_MS).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (showing && top != indexOf(out)) out.setImageDrawable(null);
            }
        }).start();
        top = fresh;
        ui.removeCallbacks(next);
        ui.postDelayed(next, SHOW_MS);
    }

    /** A slow pan and zoom across the picture, a little different each time. */
    private void drift(int slot) {
        ImageView v = images[slot];
        if (drifts[slot] != null) drifts[slot].cancel();
        float from = 1.06f + random.nextFloat() * 0.04f, to = 1.14f + random.nextFloat() * 0.06f;
        if (random.nextBoolean()) {
            float t = from;
            from = to;
            to = t;
        }
        float span = getWidth() * 0.035f;
        float x0 = (random.nextFloat() * 2 - 1) * span, x1 = (random.nextFloat() * 2 - 1) * span;
        float y0 = (random.nextFloat() * 2 - 1) * span * 0.5f, y1 = (random.nextFloat() * 2 - 1) * span * 0.5f;
        drifts[slot] = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, from, to),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, from, to),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, x0, x1),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, y0, y1));
        drifts[slot].setDuration(SHOW_MS + FADE_MS * 2);
        drifts[slot].setInterpolator(new LinearInterpolator());
        drifts[slot].start();
    }

    private int indexOf(ImageView v) {
        return v == images[0] ? 0 : 1;
    }

    private static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
