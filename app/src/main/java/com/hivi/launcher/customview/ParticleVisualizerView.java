package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleVisualizerView extends View {

    public enum State {
        IDLE, LISTENING, THINKING, SPEAKING
    }

    private static final int PARTICLE_COUNT = 480;
    private static final float LERP_FACTOR = 0.14f;
    private static final float THINK_LERP_FACTOR = 0.08f;
    private static final float BASE_RADIUS_DP = 100f;
    private static final float LISTENING_RADIUS_SCALE = 0.82f;
    private static final float LISTENING_VOLUME_CAP = 0.85f;
    private static final float PERSPECTIVE = 1000f;
    private static final boolean ENABLE_PARTICLE_GLOW = true;
    private static final float IDLE_GLOW_RADIUS_PX = 8f;
    private static final float ACTIVE_GLOW_RADIUS_PX = 6f;
    private static final int GLOW_BITMAP_SIZE = 64;
    private static final int PARTICLE_COLOR;
    private static final int PARTICLE_GLOW_COLOR;

    static {
        float[] hsv = {189f, 0.90f, 0.73f};
        PARTICLE_COLOR = Color.HSVToColor(255, hsv);
        PARTICLE_GLOW_COLOR = Color.HSVToColor(128, hsv);
    }

    private final List<Particle> particles = new ArrayList<>(PARTICLE_COUNT);
    private final Random random = new Random();
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final RectF glowRect = new RectF();
    private final float[] unitCircleX = new float[PARTICLE_COUNT];
    private final float[] unitCircleY = new float[PARTICLE_COUNT];
    private final float[] speakingUnitX = new float[PARTICLE_COUNT];
    private final float[] speakingUnitY = new float[PARTICLE_COUNT];
    private final float[] speakingUnitZ = new float[PARTICLE_COUNT];

    private State currentState = State.IDLE;
    private float currentVolume = 0f;
    private float targetVolume = 0f;
    private float baseRadius;
    private RadialGradient cachedAuraGradient;
    private float cachedAuraCenterX = -1f;
    private float cachedAuraCenterY = -1f;
    private float cachedAuraRadius = -1f;
    private int cachedAuraColor = 0;
    private Bitmap idleGlowBitmap;
    private Bitmap activeGlowBitmap;
    private Bitmap prewarmBitmap;
    private Canvas prewarmCanvas;
    private int lastPrewarmWidth = -1;
    private int lastPrewarmHeight = -1;

    public ParticleVisualizerView(Context context) {
        super(context);
        init();
    }

    public ParticleVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        baseRadius = BASE_RADIUS_DP * getResources().getDisplayMetrics().density;

        particlePaint.setStyle(Paint.Style.FILL);
        auraPaint.setStyle(Paint.Style.FILL);
        prepareStaticGeometry();
        ensureGlowBitmaps();

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            Particle particle = new Particle();
            float tx = unitCircleX[i] * (baseRadius + particle.idleSpread);
            float ty = unitCircleY[i] * (baseRadius + particle.idleSpread);
            particle.setBaseTarget(tx, ty, 0f);
            particle.x = tx;
            particle.y = ty;
            particle.z = 0f;
            particles.add(particle);
        }
    }

    private void prepareStaticGeometry() {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            double angle = (i / (double) PARTICLE_COUNT) * Math.PI * 2;
            unitCircleX[i] = (float) Math.cos(angle);
            unitCircleY[i] = (float) Math.sin(angle);

            double phi = Math.acos(-1.0 + (2.0 * i) / PARTICLE_COUNT);
            double theta = Math.sqrt(PARTICLE_COUNT * Math.PI) * phi;
            speakingUnitX[i] = (float) (Math.sin(phi) * Math.cos(theta));
            speakingUnitY[i] = (float) (Math.sin(phi) * Math.sin(theta));
            speakingUnitZ[i] = (float) Math.cos(phi);
        }
    }

    private void ensureGlowBitmaps() {
        if (idleGlowBitmap == null) {
            idleGlowBitmap = createGlowBitmap(GLOW_BITMAP_SIZE, PARTICLE_GLOW_COLOR);
        }
        if (activeGlowBitmap == null) {
            int activeGlowColor = Color.argb(
                    Math.min(255, Math.round(Color.alpha(PARTICLE_GLOW_COLOR) * 0.55f)),
                    Color.red(PARTICLE_GLOW_COLOR),
                    Color.green(PARTICLE_GLOW_COLOR),
                    Color.blue(PARTICLE_GLOW_COLOR)
            );
            activeGlowBitmap = createGlowBitmap(GLOW_BITMAP_SIZE, activeGlowColor);
        }
    }

    private Bitmap createGlowBitmap(int size, int glowColor) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        float center = size / 2f;
        float radius = size / 2f;
        int midGlowColor = Color.argb(
                Math.max(0, Math.round(Color.alpha(glowColor) * 0.22f)),
                Color.red(glowColor),
                Color.green(glowColor),
                Color.blue(glowColor)
        );
        glowPaint.setShader(new RadialGradient(
                center,
                center,
                radius,
                new int[]{glowColor, midGlowColor, Color.TRANSPARENT},
                new float[]{0f, 0.32f, 1f},
                Shader.TileMode.CLAMP
        ));
        bitmapCanvas.drawCircle(center, center, radius, glowPaint);
        return bitmap;
    }

    public void setVolume(float volume) {
        this.targetVolume = Math.max(0f, Math.min(1f, volume));
        if (getVisibility() == View.VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    public void resetVolume() {
        this.targetVolume = 0f;
        this.currentVolume = 0f;
        invalidate();
    }

    public void setState(State state) {
        if (this.currentState == state) {
            return;
        }
        this.currentState = state;
        updateParticleTargets();
        if (getVisibility() == View.VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    private float getVisualVolume(float volume, State state) {
        return state == State.LISTENING ? Math.min(volume, LISTENING_VOLUME_CAP) : volume;
    }

    private void updateParticleTargets() {
        updateParticleTargets(getWidth(), getHeight());
    }

    private void updateParticleTargets(int width, int height) {
        if (width == 0 || height == 0) {
            return;
        }

        float minDim = Math.min(width, height);
        float listeningRadius = baseRadius * LISTENING_RADIUS_SCALE;
        float speakingRadius = baseRadius * 0.9f;

        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            float tx = 0f;
            float ty = 0f;
            float tz = 0f;

            switch (currentState) {
                case IDLE:
                    tx = unitCircleX[i] * (baseRadius + particle.idleSpread);
                    ty = unitCircleY[i] * (baseRadius + particle.idleSpread);
                    break;
                case LISTENING:
                    tx = unitCircleX[i] * (listeningRadius + particle.listeningSpread);
                    ty = unitCircleY[i] * (listeningRadius + particle.listeningSpread);
                    break;
                case THINKING:
                    float thinkingRadius = particle.thinkingRadiusFactor * minDim;
                    tx = particle.thinkingUnitX * thinkingRadius;
                    ty = particle.thinkingUnitY * thinkingRadius;
                    tz = particle.thinkingTargetZ;
                    break;
                case SPEAKING:
                    tx = speakingUnitX[i] * speakingRadius;
                    ty = speakingUnitY[i] * speakingRadius;
                    tz = speakingUnitZ[i] * speakingRadius;
                    break;
            }
            particle.setBaseTarget(tx, ty, tz);
        }
    }

    public void prewarm(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (lastPrewarmWidth == width && lastPrewarmHeight == height) {
            return;
        }

        ensureGlowBitmaps();
        ensurePrewarmBuffer(width, height);
        if (prewarmCanvas == null) {
            return;
        }

        ParticleSnapshot snapshot = captureSnapshot();
        State savedState = currentState;
        float savedCurrentVolume = currentVolume;
        float savedTargetVolume = targetVolume;
        long warmTime = SystemClock.uptimeMillis();

        try {
            prewarmState(State.IDLE, 0f, width, height, warmTime);
            prewarmState(State.LISTENING, 0.45f, width, height, warmTime + 16L);
            prewarmState(State.THINKING, 0f, width, height, warmTime + 32L);
            prewarmState(State.SPEAKING, 0.65f, width, height, warmTime + 48L);
            lastPrewarmWidth = width;
            lastPrewarmHeight = height;
        } finally {
            restoreSnapshot(snapshot);
            currentState = savedState;
            currentVolume = savedCurrentVolume;
            targetVolume = savedTargetVolume;
        }
    }

    private void prewarmState(State state, float volume, int width, int height, long time) {
        currentState = state;
        currentVolume = volume;
        targetVolume = volume;
        updateParticleTargets(width, height);

        float canvasWidth = prewarmBitmap != null ? prewarmBitmap.getWidth() : width;
        float canvasHeight = prewarmBitmap != null ? prewarmBitmap.getHeight() : height;
        float scaleX = canvasWidth / width;
        float scaleY = canvasHeight / height;

        prewarmCanvas.save();
        prewarmCanvas.scale(scaleX, scaleY);
        renderFrame(prewarmCanvas, width, height, time, false);
        prewarmCanvas.restore();
    }

    private void ensurePrewarmBuffer(int width, int height) {
        int maxDim = 512;
        float scale = Math.min(1f, maxDim / (float) Math.max(width, height));
        int bitmapWidth = Math.max(1, Math.round(width * scale));
        int bitmapHeight = Math.max(1, Math.round(height * scale));
        if (prewarmBitmap != null
                && prewarmBitmap.getWidth() == bitmapWidth
                && prewarmBitmap.getHeight() == bitmapHeight) {
            return;
        }
        if (prewarmBitmap != null) {
            prewarmBitmap.recycle();
        }
        prewarmBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        prewarmCanvas = new Canvas(prewarmBitmap);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateParticleTargets();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this && visibility == View.VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        renderFrame(canvas, getWidth(), getHeight(), SystemClock.uptimeMillis(), true);
    }

    private void renderFrame(Canvas canvas, int width, int height, long time, boolean scheduleNextFrame) {
        float centerX = width / 2f;
        float centerY = height / 2f;
        float volumeFactor = targetVolume > currentVolume ? 0.35f : 0.12f;
        currentVolume += (targetVolume - currentVolume) * volumeFactor;
        float visualVolume = getVisualVolume(currentVolume, currentState);

        float auraVolume = Math.round(visualVolume * 32f) / 32f;
        float glowScale = currentState == State.IDLE ? 1f : 1f + auraVolume;
        float auraRadius = baseRadius * 2.5f * glowScale;
        int rawAlpha = (int) ((currentState == State.IDLE ? 0.05f : 0.12f + auraVolume * 0.18f) * 255);
        int quantizedAlpha = (rawAlpha / 32) * 32;
        int auraColor = Color.argb(Math.min(255, quantizedAlpha), 125, 229, 249);
        float quantizedRadius = Math.round(auraRadius / 4f) * 4f;

        if (cachedAuraGradient == null
                || cachedAuraCenterX != centerX
                || cachedAuraCenterY != centerY
                || Math.abs(cachedAuraRadius - quantizedRadius) > 4f
                || cachedAuraColor != auraColor) {
            cachedAuraGradient = new RadialGradient(
                    centerX,
                    centerY,
                    Math.max(1f, quantizedRadius),
                    auraColor,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
            );
            cachedAuraCenterX = centerX;
            cachedAuraCenterY = centerY;
            cachedAuraRadius = quantizedRadius;
            cachedAuraColor = auraColor;
            auraPaint.setShader(cachedAuraGradient);
        }
        canvas.drawCircle(centerX, centerY, Math.max(1f, quantizedRadius), auraPaint);

        boolean useIdleGlow = currentState == State.IDLE || currentState == State.LISTENING;
        Bitmap glowBitmap = useIdleGlow ? idleGlowBitmap : activeGlowBitmap;
        float glowRadius = useIdleGlow ? IDLE_GLOW_RADIUS_PX : ACTIVE_GLOW_RADIUS_PX;

        for (int i = 0, count = particles.size(); i < count; i++) {
            Particle particle = particles.get(i);
            particle.update(visualVolume, currentState, time);
            particle.draw(canvas, centerX, centerY, particlePaint, glowBitmap, glowRadius);
        }

        if (scheduleNextFrame && getVisibility() == View.VISIBLE && isAttachedToWindow()) {
            postInvalidateOnAnimation();
        }
    }

    private ParticleSnapshot captureSnapshot() {
        ParticleSnapshot snapshot = new ParticleSnapshot(particles.size());
        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            snapshot.x[i] = particle.x;
            snapshot.y[i] = particle.y;
            snapshot.z[i] = particle.z;
            snapshot.baseTargetX[i] = particle.baseTargetX;
            snapshot.baseTargetY[i] = particle.baseTargetY;
            snapshot.baseTargetZ[i] = particle.baseTargetZ;
            snapshot.targetX[i] = particle.targetX;
            snapshot.targetY[i] = particle.targetY;
            snapshot.targetZ[i] = particle.targetZ;
        }
        return snapshot;
    }

    private void restoreSnapshot(ParticleSnapshot snapshot) {
        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            particle.setBaseTarget(snapshot.baseTargetX[i], snapshot.baseTargetY[i], snapshot.baseTargetZ[i]);
            particle.x = snapshot.x[i];
            particle.y = snapshot.y[i];
            particle.z = snapshot.z[i];
            particle.targetX = snapshot.targetX[i];
            particle.targetY = snapshot.targetY[i];
            particle.targetZ = snapshot.targetZ[i];
        }
    }

    private static class ParticleSnapshot {
        final float[] x;
        final float[] y;
        final float[] z;
        final float[] baseTargetX;
        final float[] baseTargetY;
        final float[] baseTargetZ;
        final float[] targetX;
        final float[] targetY;
        final float[] targetZ;

        ParticleSnapshot(int count) {
            x = new float[count];
            y = new float[count];
            z = new float[count];
            baseTargetX = new float[count];
            baseTargetY = new float[count];
            baseTargetZ = new float[count];
            targetX = new float[count];
            targetY = new float[count];
            targetZ = new float[count];
        }
    }

    private class Particle {
        float x;
        float y;
        float z;
        float baseTargetX;
        float baseTargetY;
        float baseTargetZ;
        float targetX;
        float targetY;
        float targetZ;
        float size;
        int color;
        float seed;
        float freqFactor;
        float baseRadialX;
        float baseRadialY;
        float baseRadial3X;
        float baseRadial3Y;
        float baseRadialZ;
        float idleSpread;
        float listeningSpread;
        float thinkingUnitX;
        float thinkingUnitY;
        float thinkingRadiusFactor;
        float thinkingTargetZ;
        boolean listeningScatterEnabled;
        float listeningBurstThreshold;
        float listeningBurstDistance;
        float listeningAngleSeed;

        Particle() {
            this.seed = random.nextFloat() * 1000f;
            this.freqFactor = 0.5f + random.nextFloat() * 1.5f;
            this.size = 0.8f + random.nextFloat() * 1.2f;
            this.idleSpread = (random.nextFloat() - 0.5f) * 22f;
            this.listeningSpread = (random.nextFloat() - 0.5f) * 16f;

            double thinkingAngle = random.nextDouble() * Math.PI * 2;
            this.thinkingUnitX = (float) Math.cos(thinkingAngle);
            this.thinkingUnitY = (float) Math.sin(thinkingAngle);
            this.thinkingRadiusFactor = 0.12f + random.nextFloat() * 0.15f;
            this.thinkingTargetZ = (random.nextFloat() - 0.5f) * 400f;

            this.listeningScatterEnabled = Math.abs((float) Math.sin(seed * 0.73f)) > 0.62f;
            this.listeningBurstThreshold = 0.32f + Math.abs((float) Math.sin(seed * 1.7f)) * 0.52f;
            float burstShape = Math.abs((float) Math.sin(seed * 2.9f));
            this.listeningBurstDistance = baseRadius * (0.08f + burstShape * burstShape * 0.52f);
            this.listeningAngleSeed = seed * 4.1f;
            this.color = PARTICLE_COLOR;
        }

        void setBaseTarget(float x, float y, float z) {
            this.baseTargetX = x;
            this.baseTargetY = y;
            this.baseTargetZ = z;

            float dist2 = (float) Math.sqrt(x * x + y * y);
            if (dist2 == 0f) {
                dist2 = 1f;
            }
            this.baseRadialX = x / dist2;
            this.baseRadialY = y / dist2;

            float dist3 = (float) Math.sqrt(x * x + y * y + z * z);
            if (dist3 == 0f) {
                dist3 = 1f;
            }
            this.baseRadial3X = x / dist3;
            this.baseRadial3Y = y / dist3;
            this.baseRadialZ = z / dist3;
        }

        void update(float volume, State state, long time) {
            float factor = state == State.THINKING ? THINK_LERP_FACTOR : LERP_FACTOR;
            float visualVolume = volume;
            float intrinsicX = 0f;
            float intrinsicY = 0f;
            float intrinsicZ = 0f;

            if (state == State.IDLE) {
                float breathe = (float) Math.sin(time * 0.002f + seed) * 6f;
                intrinsicX = (baseTargetX / baseRadius) * breathe;
                intrinsicY = (baseTargetY / baseRadius) * breathe;
            } else if (state == State.LISTENING) {
                float listeningEnergy = (float) Math.sqrt(Math.min(1f, visualVolume / LISTENING_VOLUME_CAP));
                float frequency = (0.025f + listeningEnergy * 0.05f) * freqFactor;
                float amplitude = 3f + listeningEnergy * baseRadius * 0.035f;
                intrinsicX = (float) Math.sin(time * frequency + seed) * amplitude;
                intrinsicY = (float) Math.cos(time * frequency + seed) * amplitude;

                float burst = 0f;
                if (listeningScatterEnabled) {
                    burst = Math.max(0f, (listeningEnergy - listeningBurstThreshold) / (1f - listeningBurstThreshold));
                    burst *= burst;
                }
                float burstFlicker = 0.75f + Math.abs((float) Math.sin(time * 0.002f * freqFactor + seed)) * 0.25f;
                float energyPush = burst * listeningBurstDistance * burstFlicker;
                float angleOffset = (float) Math.sin(listeningAngleSeed + time * 0.0012f * freqFactor) * 0.55f;
                float cos = (float) Math.cos(angleOffset);
                float sin = (float) Math.sin(angleOffset);
                float pushX = baseRadialX * cos - baseRadialY * sin;
                float pushY = baseRadialX * sin + baseRadialY * cos;
                float tangentialPush = (float) Math.sin(time * 0.015f * freqFactor + seed)
                        * burst * baseRadius * 0.14f;
                intrinsicX += pushX * energyPush - baseRadialY * tangentialPush;
                intrinsicY += pushY * energyPush + baseRadialX * tangentialPush;
            } else if (state == State.THINKING) {
                intrinsicX = (float) Math.sin(time * 0.0012f + seed) * 35f;
                intrinsicY = (float) Math.cos(time * 0.0015f + seed) * 35f;
                intrinsicZ = (float) Math.sin(time * 0.0014f + seed) * 50f;
            } else if (state == State.SPEAKING) {
                float baseFreq = (0.02f + volume * 0.04f) * freqFactor;
                float amp = 15f + volume * 130f;
                intrinsicX = (float) Math.sin(time * baseFreq + seed) * amp;
                intrinsicY = (float) Math.cos(time * (baseFreq * 1.1f) + seed) * amp;
                intrinsicZ = (float) Math.sin(time * (baseFreq * 0.9f) + seed * 0.5f) * amp;

                float energyPush = volume * 120f;
                intrinsicX += baseRadial3X * energyPush;
                intrinsicY += baseRadial3Y * energyPush;
                intrinsicZ += baseRadialZ * energyPush;
            }

            float pulseScale = 1f;
            if (state == State.LISTENING || state == State.SPEAKING) {
                pulseScale = state == State.LISTENING ? 1f : 1f + visualVolume * 0.8f;
            }

            targetX = (baseTargetX * pulseScale) + intrinsicX;
            targetY = (baseTargetY * pulseScale) + intrinsicY;
            targetZ = baseTargetZ + intrinsicZ;

            x += (targetX - x) * factor;
            y += (targetY - y) * factor;
            z += (targetZ - z) * factor;
        }

        void draw(Canvas canvas, float centerX, float centerY, Paint paint, Bitmap glowBitmap, float glowRadius) {
            float scale = PERSPECTIVE / (PERSPECTIVE + z);
            float px = centerX + x * scale;
            float py = centerY + y * scale;
            float renderedSize = size * scale;

            if (ENABLE_PARTICLE_GLOW && glowBitmap != null) {
                float glowHalfSize = renderedSize + glowRadius * 0.65f;
                glowRect.set(px - glowHalfSize, py - glowHalfSize, px + glowHalfSize, py + glowHalfSize);
                canvas.drawBitmap(glowBitmap, null, glowRect, glowBitmapPaint);
            }

            paint.setColor(color);
            canvas.drawCircle(px, py, renderedSize, paint);
        }
    }
}
