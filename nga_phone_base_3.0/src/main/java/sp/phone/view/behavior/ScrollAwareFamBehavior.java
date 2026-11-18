package sp.phone.view.behavior;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewPropertyAnimatorListener;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.getbase.floatingactionbutton.FloatingActionsMenu;

/**
 * Created by Justwen on 2018/8/12.
 */
public class ScrollAwareFamBehavior extends com.getbase.floatingactionbutton.ScrollAwareFamBehavior {

    private static final Interpolator INTERPOLATOR = new FastOutSlowInInterpolator();
    private boolean mIsAnimatingOut = false;
    private boolean mIsAnimationIn = false;
    private boolean mIsShown = true;
    private static final int SCROLL_AXIS_VERTICAL = 10;

    public ScrollAwareFamBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, FloatingActionsMenu child, View directTargetChild, View target, int nestedScrollAxes) {
        return nestedScrollAxes == 2;
    }

    public void onNestedScroll(CoordinatorLayout coordinatorLayout, FloatingActionsMenu child, View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (dyConsumed > 10 && !this.mIsAnimatingOut && this.mIsShown) {
            child.collapse();
            animateOut(child);
        } else if (dyConsumed < -10 && !this.mIsAnimationIn && !this.mIsShown) {
            animateIn(child);
        }

    }

    public void animateOut(FloatingActionsMenu button) {
        int height = button.getAddFloatingActionButton().getHeight() + button.getAddButtonMarginBottom();
        ViewCompat.animate(button).translationY((float) height).setInterpolator(INTERPOLATOR).setListener(new ViewPropertyAnimatorListener() {
            public void onAnimationStart(View view) {
                mIsAnimatingOut = true;
            }

            public void onAnimationCancel(View view) {
                mIsAnimatingOut = false;
            }

            public void onAnimationEnd(View view) {
                mIsAnimatingOut = false;
                mIsShown = false;
            }
        }).start();
    }

    /**
     * Fork：不带动画地收起（进帖子时先不显示悬浮按钮，往上滑或翻页后再出来）。
     * 按钮还没布局、量不出高度，所以在第一次绘制前再挪下去。
     */
    public void hideNow(FloatingActionsMenu button) {
        mIsShown = false;
        button.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                button.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!mIsShown && !mIsAnimationIn) {
                    button.setTranslationY(button.getAddFloatingActionButton().getHeight() + button.getAddButtonMarginBottom());
                }
                return true;
            }
        });
    }

    public void animateIn(FloatingActionsMenu button) {
        if (mIsAnimationIn || mIsShown) {
            return;
        }
        button.setVisibility(View.VISIBLE);
        ViewCompat.animate(button).translationY(0.0F).setInterpolator(INTERPOLATOR).setListener(new ViewPropertyAnimatorListener() {
            public void onAnimationStart(View view) {
                mIsAnimationIn = true;
            }

            public void onAnimationEnd(View view) {
                mIsShown = true;
                mIsAnimationIn = false;
            }

            public void onAnimationCancel(View view) {
                mIsAnimationIn = false;
            }
        }).start();
    }


}
