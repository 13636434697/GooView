package com.xu.gooview;

import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/*
* 需要自己手工绘制效果，自定义一个view比较好，如果自定义viewgroup的话，要自定义怎么摆放子view了
*
*
* */
public class GooView extends View{
	//初始化画笔对象
	private Paint paint;
	//实现构造方法
	public GooView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		//初始化一个方法，唉每个构造方法里面调用一次
		init();
	}
	//实现构造方法
	public GooView(Context context, AttributeSet attrs) {
		super(context, attrs);
		//初始化一个方法，唉每个构造方法里面调用一次
		init();
	}
	//实现构造方法
	public GooView(Context context) {
		super(context);
		//初始化一个方法，唉每个构造方法里面调用一次
		init();
	}

	//初始化一个方法
	private void init(){
		//初始化画笔对象
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);//设置抗锯齿
		//设置一个画笔颜色
		paint.setColor(Color.RED);
	}
	private float dragRadius = 12f;//拖拽圆的半径
	private float stickyRadius = 12f;//固定圆的半径
	//点的封装是float类型的，用来封装xy的
	private PointF dragCenter = new PointF(100f, 120f);//拖拽圆的圆心
	//在一个园，粘性的园，不动的
	private PointF stickyCenter = new PointF(150f, 120f);//固定圆的圆心

	//每个园会有2个点，现在定义四个点。
	private PointF[] stickyPoint = {new PointF(150f, 108f),new PointF(150f, 132f)};
	private PointF[] dragPoint = {new PointF(100f, 108f),new PointF(100f, 132f)};

	//2圆中间点的距离
	private PointF controlPoint = new PointF(125f, 120f);
	private double lineK;//斜率

	//绘制一个东西，就在这个方法里面写
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		//让整体画布往上偏移,x方向不用变，偏移状态值的高度，是负数
		canvas.translate(0, -Utils.getStatusBarHeight(getResources()));
		
		stickyRadius = getStickyRadius();
		
		//dragPoint: 2圆圆心连线的垂线与drag圆的交点
		//stickyPoint: 2圆圆心连线的垂线与sticky圆的交点
		//根据dragCenter动态求出dragPoint和stickyPoint
		float xOffset = dragCenter.x - stickyCenter.x;
		float yOffset = dragCenter.y - stickyCenter.y;
		if(xOffset!=0){
			//求偏移值：斜率
			lineK = yOffset/xOffset;
		}
		//这个point是动态的了，用几何工具类（圆心，半径，斜率）
		dragPoint = GeometryUtil.getIntersectionPoints(dragCenter, dragRadius, lineK);
		stickyPoint = GeometryUtil.getIntersectionPoints(stickyCenter, stickyRadius, lineK);
		
		//动态计算控制点，0.618黄金分割点
		controlPoint = GeometryUtil.getPointByPercent(dragCenter, stickyCenter, 0.618f);
		
		//1.绘制2个圆（圆心的坐标的x和y，半径，画笔对象）
		canvas.drawCircle(dragCenter.x, dragCenter.y, dragRadius, paint);//绘制拖拽圆

		//没有超出范围，才绘制贝塞尔曲线曲线
		if(!isDragOutOfRange){
			canvas.drawCircle(stickyCenter.x, stickyCenter.y, stickyRadius, paint);//绘制固定圆
			//2.使用贝塞尔曲线绘制连接部分，（2园点连接的部分）
			Path path = new Path();

			path.moveTo(stickyPoint[0].x, stickyPoint[0].y);//设置起点
			//控制点的X和Y
			path.quadTo(controlPoint.x, controlPoint.y, dragPoint[0].x, dragPoint[0].y);//使用贝塞尔曲线连接
			//point的第二个点中间是有连线的，
			path.lineTo(dragPoint[1].x, dragPoint[1].y);
			//控制点的X和Y，第二给点
			path.quadTo(controlPoint.x, controlPoint.y, stickyPoint[1].x, stickyPoint[1].y);
			//		path.close();//默认会闭合，所以不用掉
			//绘制一下这个path
			canvas.drawPath(path, paint);
		}


		//绘制圈圈，以固定圆圆心为圆心，然后80为半径
		paint.setStyle(Style.STROKE);//设置只有边线
		//这样是实心圆，在上面设置一下
		canvas.drawCircle(stickyCenter.x, stickyCenter.y, maxDistance, paint);
		//设置布局
		paint.setStyle(Style.FILL);
	}
	
	private float maxDistance = 80;
	/**
	 * 根据2个圆动态求出固定圆的半径，因为起始点园需要变小，根据2个园之间的距离变化
	 */
	private float getStickyRadius(){
		float radius;
		//求出2个圆心之间的距离
		float centerDistance = GeometryUtil.getDistanceBetween2Points(dragCenter, stickyCenter);
		//根据距离来变化，设置一个最大距离，最大距离的百分比
		float fraction = centerDistance/maxDistance;//圆心距离占总距离的百分比
		//根据百分比来算半径，百分比，半径的起始值，半径结束值
		radius = GeometryUtil.evaluateValue(fraction, 12f, 4f);
		return radius;
	}

	//标记是否超过拖拽范围
	private boolean isDragOutOfRange = false;
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			//标记是否超过拖拽范围。这里在重置一下
			isDragOutOfRange = false;
			//设置按下的点用getRawX好点，取屏幕的坐标
			dragCenter.set(event.getRawX(), event.getRawY());
			break;
		case MotionEvent.ACTION_MOVE:
			dragCenter.set(event.getRawX(), event.getRawY());
			//拖动超出范围，取消贝塞尔曲线
			if(GeometryUtil.getDistanceBetween2Points(dragCenter, stickyCenter)>maxDistance){
				//超出范围，应该断掉，不再绘制贝塞尔曲线的部分
				isDragOutOfRange = true;
			}
			
			break;
		case MotionEvent.ACTION_UP:
			//超出拖拽范围
			if(GeometryUtil.getDistanceBetween2Points(dragCenter, stickyCenter)>maxDistance){
				//重新设置，固定元的圆心
				dragCenter.set(stickyCenter.x, stickyCenter.y);
			}else {
				//如果曾经超出范围过
				if(isDragOutOfRange){
					//重新设置，固定元的圆心
					dragCenter.set(stickyCenter.x, stickyCenter.y);
				}else {
					//动画的形式回去
					//属性动画，执行了流程，具体可以做任何事情，这里不能用int，是一个点的变化，填写1是随便填写的
					ValueAnimator valueAnimator = ObjectAnimator.ofFloat(1);
					final PointF startPointF = new PointF(dragCenter.x, dragCenter.y);
					//不能通过values的值
					valueAnimator.addUpdateListener(new AnimatorUpdateListener() {
						@Override
						public void onAnimationUpdate(ValueAnimator animator) {
							//d动画执行的百分比
							float animatedFraction = animator.getAnimatedFraction();
							Log.e("tag", "animatedFraction: "+animatedFraction);
							//2个点之间的过度，根据2个点求出百分比的位置，起始点是当前拖拽的圆心点，终点是固定园，百分比，返回一个动画当前的点
							PointF pointF = GeometryUtil.getPointByPercent(startPointF, stickyCenter, animatedFraction);
							//直接传个点
							dragCenter.set(pointF);
							//重新绘制要刷新
							invalidate();
						}
					});
					valueAnimator.setDuration(500);
					//弹性要设置插值器，要超过一点的插值器
					valueAnimator.setInterpolator(new OvershootInterpolator(3));
					valueAnimator.start();
				}
			}
			break;
		}
		//在这里统一获取，然后在统一刷新一下所有的点
		invalidate();
		//需要返回true。这样这样move事件才能接收到
		return true;
	}

}
