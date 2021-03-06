package com.cjt2325.kotlin_jcameraview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * =====================================
 * 作    者: 陈嘉桐
 * 版    本：
 * 创建日期：2017/8/9
 * 描    述：拍照按钮
 * =====================================
 */
class CaptureButton(context: Context, size: Float) : View(context) {

    private var state: Int;
    private val STATE_IDLE = 0x001//空闲状态
    private val STATE_PRESS = 0x002//按下状态
    private val STATE_LONG_PRESS = 0x003//进入长按状态
    private val STATE_RECORDERING = 0x004//录制状态

    private var mPaint: Paint = Paint()

    private var button_size: Float //控件大小
    private val strokeWidth: Float //进度条宽度

    private var button_outside_radius: Float //外圆半径
    private var button_inside_radius: Float //内圆半径

    private val outside_add_size: Float //长按外圆半径变大的Size
    private val inside_reduce_size: Float //长安内圆缩小的Size

    private var center_X: Float //X轴中心
    private var center_Y: Float //Y轴中心

    private var touch_Y: Float = 0f//Touch_Event_Down时候记录的Y值

    var max_duration = 5000//录制最长时间，默认为10s
    var progress_color: Int = 0xEE16AE16.toInt()//进度条颜色
    var outside_color: Int = 0xEECCCCCC.toInt()//外圆背景色
    var inside_color: Int = 0xFFFFFFFF.toInt()//内圆背景色


    private var progress: Float = 0f//录制视频的进度
    private val rectF: RectF
    private var record_anim = ValueAnimator.ofFloat(0f, 361f)

    /**
     * 主构造函数初始化代码块
     */
    init {
        state = STATE_IDLE

        button_size = size
        button_outside_radius = size / 2
        button_inside_radius = size / 2 * 0.75f

        strokeWidth = size / 15 //精度条宽度
        outside_add_size = size / 5 //长按外圆增加的半径
        inside_reduce_size = size / 8 //长按内圆减少的半径
        //控件中心
        center_X = (button_size + outside_add_size * 2) / 2
        center_Y = (button_size + outside_add_size * 2) / 2
        //精度条矩形范围
        rectF = RectF(
                center_X - (size / 2 + outside_add_size - strokeWidth / 2),
                center_Y - (size / 2 + outside_add_size - strokeWidth / 2),
                center_X + (size / 2 + outside_add_size - strokeWidth / 2),
                center_Y + (size / 2 + outside_add_size - strokeWidth / 2))

        mPaint.isAntiAlias = true//抗锯齿

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mPaint.style = Paint.Style.FILL
        mPaint.color = outside_color//外圆（默认半透明灰色）
        canvas.drawCircle(center_X, center_Y, button_outside_radius, mPaint)
        mPaint.color = inside_color//内圆（默认白色）
        canvas.drawCircle(center_X, center_Y, button_inside_radius, mPaint)

        //如果状态为按钮长按按下的状态，则绘制录制进度条
        if (state == STATE_RECORDERING) {
            mPaint.color = progress_color
            mPaint.style = Paint.Style.STROKE
            mPaint.strokeWidth = strokeWidth
            canvas.drawArc(rectF, -90f, progress, false, mPaint)
        }
    }



    //录视频线程
    private val recordRunnable = Runnable {
        state = STATE_RECORDERING
        record_anim.addUpdateListener { animation ->
            if (state == STATE_RECORDERING) {
                //更新录制进度
                progress = animation.getAnimatedValue() as Float
                invalidate()
            } else {
//                TODO("这里应该停止录像并且重置")
            }
            Log.i("CJT","Running")
        }
        record_anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                if (state == STATE_RECORDERING) {
                    recordEnd(true)
                    Log.i("CJT", "onAnimationEnd")
                }
            }
        })
        record_anim.duration = max_duration.toLong()
        record_anim.interpolator = LinearInterpolator()
        record_anim.start()
    }
    //长按线程
    private val longPressRunnable = Runnable {
        state = STATE_LONG_PRESS //状态为长按状态
        startAnimation(
                button_outside_radius,
                button_outside_radius + outside_add_size,
                button_inside_radius,
                button_inside_radius - inside_reduce_size
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(
                (button_size + outside_add_size * 2).toInt(),
                (button_size + outside_add_size * 2).toInt())
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touch_Y = event.y //记录Y值
                state = STATE_PRESS //状态为按下状态
                postDelayed(longPressRunnable, 500)
            }
            MotionEvent.ACTION_MOVE -> {

            }
            MotionEvent.ACTION_UP -> {
                handlerUnpressByState()
            }
        }
        return true
    }

    /**
     * @param outside_start 外圆起始半径
     * @param outside_end   外圆结束半径
     * @param inside_start  内圆起始半径
     * @param inside_end    内圆结束半径
     */
    private fun startAnimation(outside_start: Float, outside_end: Float, inside_start: Float, inside_end: Float) {
        val outside_anim = ValueAnimator.ofFloat(outside_start, outside_end)
        val inside_anim = ValueAnimator.ofFloat(inside_start, inside_end)
        //外圆
        outside_anim.addUpdateListener { animation ->
            button_outside_radius = animation.animatedValue as Float
            invalidate()
        }
        //内圆
        inside_anim.addUpdateListener { animation ->
            button_inside_radius = animation.animatedValue as Float
            invalidate()
        }
        //当动画结束后启动录像Runnable并且回调录像开始接口

        var animSet: AnimatorSet = AnimatorSet()
        animSet.playTogether(outside_anim, inside_anim)
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                if (state == STATE_LONG_PRESS) {
//                    if (captureLisenter != null) {
//                        captureLisenter.recordStart()
//                    }
                    post(recordRunnable)
                }
            }
        })
        animSet.duration = 100
        animSet.start()
    }

    private fun handlerUnpressByState() {
        removeCallbacks(longPressRunnable)//移除长按逻辑的Runnable
        when (state) {
            STATE_PRESS -> Log.i("CJT", "拍照")
//                if (captureLisenter != null && (button_state == BUTTON_STATE_ONLY_CAPTURE || button_state == BUTTON_STATE_BOTH)) {
//                //回调拍照接口
//                    captureLisenter.takePictures()
//                }
            STATE_RECORDERING -> {
                Log.i("CJT", "录制")
                removeCallbacks(recordRunnable)//移除录制视频的Runnable
                recordEnd(false)//录制结束
            }
        }
        this.state = STATE_IDLE //制空当前状态
    }

    /**
     * @param finish 是否录制最大时间
     */
    private fun recordEnd(finish: Boolean) {
//        if (captureLisenter != null) {
//            //录制时间小于一秒时候则提示录制时间过短
//            if (record_anim.currentPlayTime < 1500 && !finish) {
//                captureLisenter.recordShort(record_anim.currentPlayTime)
//            } else {
                if (finish) {
                    Log.i("CJT", "录制了 = " + max_duration)
//                    captureLisenter.recordEnd(duration.toLong())
                } else {
//                    captureLisenter.recordEnd(record_anim.currentPlayTime)
                    Log.i("CJT", "录制了 = " + record_anim.currentPlayTime)
                }
//            }
//        }

        resetRecordAnim()
    }

    fun resetRecordAnim() {
        record_anim.cancel()
        progress = 0f
        invalidate()
        startAnimation(
                button_outside_radius,
                button_size / 2,
                button_inside_radius,
                button_size / 2 * 0.75f
        )
    }
}