package org.fossify.camera.drawcore;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;

/**
 * 裁剪自 Telegram Android 客户端同名 CanvasButton 组件。
 *
 * 保留的核心逻辑：drawingRects 收集 / usingRectCount 计数 / addRect / setRect / rewind /
 * contains 命中检测 / checkTouchEvent 状态机（DOWN 命中->pressed，UP/CANCEL->回调+复位，MOVE）/
 * longPressRunnableInner 长按计时与触觉反馈 / drawInternal 的多矩形 CornerPath 合并路径与单矩形圆角绘制。
 *
 * 原工程依赖替换映射（原文用法 -> 本类方案）：
 *  - dp(12) / dp(4)（原工程像素密度工具）           -> DpUtils.dp(12) / DpUtils.dp(4)（@JvmStatic，int 返回）
 *  - runOnUIThread(r, longPressTimeout)（原工程主线程调度） -> UIThread.post(r, delayMillis: Long)
 *  - cancelRunOnUIThread(r)（原工程主线程调度）            -> UIThread.cancel(r)
 *  - rectTmp（原工程共享 scratch，setRect(int,int,int,int)） -> 本类 private static final RectF rectTmp（scratch，addRect 立即拷贝，语义一致）
 *  - 主题色 listSelector 取色                             -> 删除；常态/按下色改为 normalColor / pressedColor 两个 int 字段
 *  - setSelectorDrawableColor(selector, c, true)          -> setColor(color, selectorColor) 直接写入 normalColor/pressedColor 字段
 *  - RippleDrawableSafe + maskDrawable + maskPaint + ColorStateList + RippleDrawable
 *                                                         -> 整套 ripple 机制删除；pressed 反馈改为 drawInternal 入口处
 *                                                            paint.setColor(buttonPressed ? pressedColor : normalColor) + parent.invalidate()
 */
public class CanvasButton {

    CornerPath drawingPath;
    ArrayList<RectF> drawingRects = new ArrayList<>();
    int usingRectCount;
    boolean buttonPressed;

    private final View parent;
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Runnable delegate;
    private boolean pathCreated;
    Runnable longPressRunnable;
    Runnable longPressRunnableInner = new Runnable() {
        @Override
        public void run() {
            checkTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));
            parent.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (longPressRunnable != null) {
                longPressRunnable.run();
            }
        }
    };
    private boolean longPressEnabled;
    CornerPathEffect pathEffect;
    boolean rounded;
    float roundRadius = DpUtils.dp(12);

    /** 常态按钮填充色（默认半透明白）。 */
    private int normalColor = 0x40FFFFFF;
    /** 按下态按钮填充色（默认更亮的半透明白）。由 setColor(color, selectorColor) 第二参数覆盖。 */
    private int pressedColor = 0x99FFFFFF;

    /** 对齐原工程共享 rectTmp 的静态 scratch：仅在 setRect(int,int,int,int) 中使用，addRect 会立即 rect.set() 拷贝。 */
    private static final RectF rectTmp = new RectF();

    public CanvasButton(View parent) {
        this.parent = parent;
        paint.setPathEffect(pathEffect = new CornerPathEffect(roundRadius));
        paint.setColor(normalColor);
    }


    public void draw(Canvas canvas) {
        drawInternal(canvas, paint);
    }

    private void drawInternal(Canvas canvas, Paint paint) {
        // pressed 反馈：按下时整体切到 pressedColor，替代原 selectorDrawable(ripple) 的顶层绘制。
        paint.setColor(buttonPressed ? pressedColor : normalColor);
        if (usingRectCount > 1) {
            if (!pathCreated) {
                if (drawingPath == null) {
                    drawingPath = new CornerPath(2);
                } else {
                    drawingPath.rewind();
                }
                int left = 0, top = 0, right = 0, bottom = 0;
                for (int i = 0; i < usingRectCount; i++) {
                    if (i + 1 < usingRectCount) {
                        float rightCurrent = drawingRects.get(i).right;
                        float rightNext = drawingRects.get(i + 1).right;
                        if (Math.abs(rightCurrent - rightNext) < DpUtils.dp(4)) {
                            drawingRects.get(i + 1).right = drawingRects.get(i).right = Math.max(rightCurrent, rightNext);
                        }
                    }
                    if (i == 0 || drawingRects.get(i).bottom > bottom) {
                        bottom = (int) drawingRects.get(i).bottom;
                    }
                    if (i == 0 || drawingRects.get(i).right > right) {
                        right = (int) drawingRects.get(i).right;
                    }
                    if (i == 0 || drawingRects.get(i).left < left) {
                        left = (int) drawingRects.get(i).left;
                    }
                    if (i == 0 || drawingRects.get(i).top < top) {
                        top = (int) drawingRects.get(i).top;
                    }
                    drawingPath.addRect(drawingRects.get(i), Path.Direction.CCW);
                }
                drawingPath.closeRects();
                pathCreated = true;
            }
            paint.setPathEffect(pathEffect);
            if (drawingPath != null) {
                canvas.drawPath(drawingPath, paint);
            }
        } else if (usingRectCount == 1) {
            if (rounded) {
                paint.setPathEffect(null);
                float rad = Math.min(drawingRects.get(0).width(), drawingRects.get(0).height()) / 2f;
                canvas.drawRoundRect(drawingRects.get(0), rad, rad, paint);
            } else {
                paint.setPathEffect(pathEffect);
                canvas.drawRoundRect(drawingRects.get(0), 0, 0, paint);
            }
        }
    }

    public boolean checkTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (contains(x, y)) {
                buttonPressed = true;
                UIThread.cancel(longPressRunnableInner);
                if (longPressEnabled) {
                    UIThread.post(longPressRunnableInner, ViewConfiguration.getLongPressTimeout());
                }
                parent.invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (buttonPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.run();
                }
                parent.playSoundEffect(SoundEffectConstants.CLICK);
                buttonPressed = false;
                parent.invalidate();
            }
            UIThread.cancel(longPressRunnableInner);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            // 原 selectorDrawable.setHotspot(...) 的 ripple 跟随逻辑已随 ripple 机制一并移除。
        }
        return buttonPressed;
    }

    private boolean contains(int x, int y) {
        for (int i = 0; i < usingRectCount; i++) {
            if (drawingRects.get(i).contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    public void setColor(int color) {
        setColor(color, color);
    }

    public void setColor(int color, int selectorColor) {
        this.normalColor = color;
        this.pressedColor = selectorColor;
    }

    public void setDelegate(Runnable delegate) {
        this.delegate = delegate;
    }

    public void rewind() {
        pathCreated = false;
        usingRectCount = 0;
    }

    public void addRect(RectF rectF) {
        usingRectCount++;
        if (usingRectCount > drawingRects.size()) {
            drawingRects.add(new RectF());
        }
        RectF rect = drawingRects.get(usingRectCount - 1);
        rect.set(rectF);
    }

    public void setRect(RectF rectF) {
        rewind();
        addRect(rectF);
    }

    public void setLongPress(Runnable runnable) {
        longPressEnabled = true;
        longPressRunnable = runnable;
    }

    public void setRounded(boolean rounded) {
        this.rounded = rounded;
    }

    public void setRoundRadius(int radius) {
        roundRadius = radius;
        pathEffect = new CornerPathEffect(radius);
    }

    /** ripple 机制已删除，保留空实现以兼容原调用方（cancelRipple 不再有 state 可重置）。 */
    public void cancelRipple() {
        // no-op
    }

    public void setRect(int x, int y, int x1, int y1) {
        rectTmp.set(x, y, x1, y1);
        setRect(rectTmp);
    }
}
