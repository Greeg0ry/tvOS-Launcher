package com.techgenius.tvoslauncher.present;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real decoder/player checks; temporary media never enter the saved playlist. */
public final class PresentationImageChecks extends Instrumentation {
    PresentationActivity player;
    final List<String> temporaryMedia = new ArrayList<>();
    final List<String> failures = new ArrayList<>();
    int checks;
    void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(message);
    }
    void ui(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    void await(BooleanSupplier condition, String message) {
        long deadline = android.os.SystemClock.uptimeMillis() + 8000;
        AtomicBoolean ready = new AtomicBoolean();
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            ui(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) { checks++; return; }
            android.os.SystemClock.sleep(50);
        }
        check(false, message);
    }
    Model.Media upload(File file, String name) throws Exception {
        Model.Media media = Presenter.repo(getTargetContext()).saveUpload(file, name, "image/png");
        temporaryMedia.add(media.id);
        return media;
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        StringBuilder details = new StringBuilder();
        try {
            int i = 0;
            for (Model.Media media : Presenter.repo(getTargetContext()).listMedia()) {
                if (!Model.IMAGE.equals(media.kind())) continue;
                File file = Presenter.repo(getTargetContext()).file(media);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                details.append("image#").append(++i).append(" bytes=").append(file.length())
                        .append(" source=").append(bounds.outWidth).append('x').append(bounds.outHeight).append('\n');
            }
            details.append("Stored presentation images: ").append(i).append('\n');
            player = (PresentationActivity) startActivitySync(new Intent(getTargetContext(), PresentationActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(() -> { player.playing = false; player.handler.removeCallbacks(player.nextRunnable); player.hideConnection(); });
            File tall = File.createTempFile("presentation-check-tall", ".png", getTargetContext().getCacheDir());
            Bitmap fixture = Bitmap.createBitmap(128, 12000, Bitmap.Config.ARGB_8888);
            fixture.eraseColor(0xFF2888BB);
            try (FileOutputStream output = new FileOutputStream(tall)) { fixture.compress(Bitmap.CompressFormat.PNG, 100, output); }
            fixture.recycle();
            Model.Media tallMedia = upload(tall, "presentation-check-tall.png");
            Bitmap decoded = player.decodeForScreen(Presenter.repo(player).file(tallMedia).getAbsolutePath());
            check(decoded != null, "Tall image failed to decode");
            int longestEdge = Math.max(player.getResources().getDisplayMetrics().widthPixels,
                    player.getResources().getDisplayMetrics().heightPixels);
            details.append("tall decoded=").append(decoded.getWidth()).append('x').append(decoded.getHeight())
                    .append(" screenEdge=").append(longestEdge).append('\n');
            check(decoded.getWidth() <= longestEdge && decoded.getHeight() <= longestEdge,
                    "Decoded image exceeds screen/GPU bounds: " + decoded.getWidth() + "x" + decoded.getHeight());
            decoded.recycle();
            File corrupt = File.createTempFile("presentation-check-invalid", ".png", getTargetContext().getCacheDir());
            try (FileOutputStream output = new FileOutputStream(corrupt)) { output.write(new byte[]{1, 2, 3, 4}); }
            Model.Media badMedia = upload(corrupt, "presentation-check-invalid.png");
            Model.Slide badSlide = new Model.Slide();
            badSlide.type = Model.IMAGE; badSlide.mediaId = badMedia.id; badSlide.durationSec = 10;
            ui(() -> { player.slides = Collections.singletonList(badSlide); player.showSlide(0); });
            long errorDeadline = android.os.SystemClock.uptimeMillis() + 8000;
            while (!"error".equals(player.status().state) && android.os.SystemClock.uptimeMillis() < errorDeadline) {
                android.os.SystemClock.sleep(50);
            }
            ui(() -> {
                check("error".equals(player.status().state), "Invalid image must not report playing on a black surface");
                check(player.messageCard.getVisibility() == View.VISIBLE, "Invalid image should show a clear skip message");
            });
            Model.Slide goodSlide = new Model.Slide();
            goodSlide.type = Model.IMAGE; goodSlide.mediaId = tallMedia.id; goodSlide.durationSec = 30;
            ui(() -> { player.slides = java.util.Arrays.asList(goodSlide, badSlide); player.showSlide(0); });
            await(() -> goodSlide.id.equals(player.status().slideId) && player.imageView.getDrawable() != null,
                    "Valid image must become visible after a failed slide");
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            player.imageIo.execute(() -> { try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); } });
            android.graphics.drawable.Drawable previous = player.imageView.getDrawable();
            try {
                ui(() -> {
                    player.showSlide(0);
                    check(player.imageView.getDrawable() == previous && player.imageView.getVisibility() == View.VISIBLE,
                            "Previous frame must remain visible while the next image decodes");
                });
            } finally { release.countDown(); }
            await(() -> player.imageView.getDrawable() != previous && player.imageView.getDrawable() != null,
                    "Prepared replacement image did not publish");
            ui(() -> { player.showSlide(1); player.showSlide(0); });
            await(() -> goodSlide.id.equals(player.status().slideId) && !"error".equals(player.status().state)
                    && player.messageCard.getVisibility() == View.GONE,
                    "A late failed decode must not replace the newly selected valid slide");
            ui(() -> { player.playing = true; player.showSlide(1); });
            await(() -> "error".equals(player.status().state), "Failed image must report its error");
            await(() -> goodSlide.id.equals(player.status().slideId) && "playing".equals(player.status().state),
                    "Playback must advance automatically after an invalid image");
            ui(() -> player.playing = false);
            if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
            result.putString("stream", details + "PASS: " + checks + " presentation image checks\n");
        } catch (Throwable error) {
            result.putString("stream", details + "FAIL: " + android.util.Log.getStackTraceString(error));
        } finally {
            if (player != null) ui(() -> player.finish());
            for (String id : temporaryMedia) Presenter.repo(getTargetContext()).deleteMedia(id);
        }
        finish(Activity.RESULT_OK, result);
    }
}
