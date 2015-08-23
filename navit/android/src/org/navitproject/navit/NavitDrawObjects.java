/**
 * Navit, a modular navigation system.
 * Copyright (C) 2005-2008 Navit Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */




package org.navitproject.navit;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.FloatMath;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.GestureDetector;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** 
 *
 * @brief This function is used to draw the objects to a canvas, if threads are activated. 
 * Drawing the objects can be done in multiple threads, but the performance is not good because the sync after each layer takes a very long time. 
 * One thread for object drawing is the best for now.
 *
 * @author Sascha Oedekoven (07/2015)
 */



public class NavitDrawObjects extends Thread 
{
	
	/** \brief A global queue. All objects are inserted into this queue.*/
	private LinkedBlockingQueue<NavitDrawObject> draw_obj_list = new LinkedBlockingQueue<NavitDrawObject>();

	/** \brief A local queue. It is used to take load off the draw_obj_list queue.*/
	private ArrayList<NavitDrawObject> local_obj_list;
	
	
	/** \brief The thread is activ, until run == false.*/
	public boolean run;
	
	/** \brief Access to the NavitGraphics class*/
	private static NavitGraphics ng;
	
	/** \brief Each thread has its own Canvas to draw on.*/
	private static Canvas[] priv_canvas;
	/** \brief Each thread has its own Bitmap to draw on*/
	private static Bitmap[] priv_bitmap;
	
	/** \brief Local paint object for drawing the polylines*/
	private Paint paint_polyline;
	/** \brief Local paint object for drawing the polygones*/
	private Paint paint_polygon;
	
	/** \brief Local paint object for drawing text*/
	private Paint paint_text;
	
	/** \brief Local path object. Its once initialized and then just reused.*/
	private Path path;
	/** \brief Local paint object to draw image and circle.*/
	private Paint paint;
	
	/** \brief Bitmap width*/
	private int w;
	/** \brief Bitmap height*/
	private int h;
	/** \brief Number of activ threads*/
	private int thread_num;
	/** \brief Index of this thread*/
	private int idx;
	

	
	
	
	/** The constructor does some initialization. 
	 *
	 * The local bitmaps and canvas will be initialized.
	 * The paint and path objects too.
	 *
	 * The thread will not be started in this function. (A separate call of this.start() is needed)
	 *
	 * @param w				Width of the map (screen)
	 * @param h				Height of the map (screen)
	 * @param navitgraphics	Grants access to the screen bitmap and canvas.
	 * @param thread_num	Number of threads in total.
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public NavitDrawObjects(int w, int h, NavitGraphics navitgraphics, int thread_num) {
		
		//time measurement:
		//start = android.os.SystemClock.elapsedRealtime();

		
		if(ng == null) {
			ng = navitgraphics;
		}
		
		/* init queue and paint/path objects */
		local_obj_list = new ArrayList<NavitDrawObject>();
		paint_polyline = new Paint();
		
		paint_polyline.setStrokeCap(Paint.Cap.ROUND);      
		paint_polyline.setStyle(Paint.Style.STROKE);
		
		paint_polygon = new Paint();
		paint_polygon.setStyle(Paint.Style.FILL);
		
		paint_text = new Paint();
		paint = new Paint();
		
		path = new Path();
		
		
		this.thread_num = thread_num;
		this.w = w;
		this.h = h;
		
		idx = thread_num-1;
		
		/* create new bitmap / canvas only if thread num has changed or its the first call*/
		if(priv_canvas == null || priv_canvas.length != NavitDrawObjectsPool.thread_n) {
			priv_canvas = new Canvas[NavitDrawObjectsPool.thread_n];
			priv_bitmap = new Bitmap[NavitDrawObjectsPool.thread_n];

		}
		
		if(NavitDrawObjectsPool.thread_n == 1) {
			
			//if just one thread is used, directly draw to the screen bitmap! (no need to copy the whole bitmap at the end -> we save 16ms)
			priv_bitmap[0] = ng.draw_bitmap;
			priv_canvas[0] = ng.draw_canvas;
			
		} else {
			
			//create private bitmap per thread and draw to this one. combine bitmaps at the end of every layer / at the end of the drawing process
			if(priv_canvas[idx] == null) {
				priv_bitmap[idx] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
				priv_canvas[idx] = new Canvas(priv_bitmap[idx]);
			} else {
				priv_bitmap[idx].eraseColor(0);
			}
			
		}
		
		run = true;
	}

	/** The function is the main loop of the thread. It handels all objects from the queue until run == false.
	 *
	 * This function gets up to 50 objects from the draw_obj_list queue to the local_obj_list queue.
	 * Its done because of performance issue.
	 * The local queue will be processed until its empty.
	 * Then the whole process starts all over again.
	 *
	 * If the run variable is set to false (cancel_draw() function), then the threads stops.
	 *
	 * The threads stops also when all objects are drawn and the local bitmaps are drawn to the screen (draw_to_screen() function).
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void run() {
		while(run) {

				draw_obj_list.drainTo(local_obj_list, 50);
				
			
				for (NavitDrawObject obj : local_obj_list) {
				
				
			
					switch(obj.type) {
						case POLYLINE:
							draw_polyline(obj.paint, obj.c);
							break;
						case POLYGON:
							draw_polygon(obj.paint, obj.c);
							break;
						case TEXT:
							draw_text(obj.x, obj.y, obj.text, obj.size, obj.dx, obj.dy, obj.bgcolor, obj.lw, obj.fgcolor);
							break;
						case CIRCLE:
							draw_circle(obj.paint, obj.x, obj.y, obj.r);
							break;
						case IMAGE:
							draw_image(obj.paint, obj.x, obj.y, obj.bitmap);
							break;
						case TOSCREEN:
							draw_to_screen(obj.mode);
							break;
						default:
							Log.e("NavitDrawObjects", "unknown element!!");
					}

					

				}
				local_obj_list.clear(); 


		}
	}
	
	/** The function stops this thread immediately.
	 *
	 * This function will be called before drawing a new selection, 
	 * if an old selection is still drawing, it will be canceled.
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void cancel_draw() {
		
		run = false;

	}
	
	/** This function draws the local bitmaps to the screen bitmap. And can save the map to a cached_bitmap.
	 *
	 * Parameter mode set to 1, the private bitmap will be drawn to the screen.
	 * With the mode parameter set to 2, the current map will be saved to the cached_bitmap in NavitGraphics.
	 *
	 * This function also can be used to synchronize after each layer.
	 * But this is currently disabled, because it does not perform well.
	 *
	 * @param mode	1 - draw to screen, 2 - cache the current map
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void draw_to_screen(int mode) {
		

		if(mode == 1) {
			
			if(ng.in_map) {
					if(NavitDrawObjectsPool.thread_n != 1) {
						ng.draw_canvas.drawBitmap(priv_bitmap[idx], 0, 0, paint);
					}
					ng.zoomFactor = 1;
					ng.viewInvalidate();
				}
					
			run = false;
			
			//time measurement:
			//long duration =  android.os.SystemClock.elapsedRealtime() - start;
			//Log.e("NavitDrawObjects", "Drawing time (java): "  + duration + " ms (Thread " + thread_num + ")");


			this.interrupt();

			
		} else if(mode == 2) {
			//save rendered map
			ng.cached_canvas.drawBitmap(ng.draw_bitmap, 0, 0, paint);
			
			
		}

	}
	
	/** This function is used to add a object from NavitDrawObjectsPool to the queue.
	 *
	 * Its used the function offer() to add the object.
	 * Its a non blocking function, which fails if the queue is full.
	 * That never should happend here.
	 *
	 *
	 * @param obj	The object which should be added to the queue and be later drawn to the screen.
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void add_object(NavitDrawObject obj) {
		
		if(draw_obj_list.offer(obj) == false) {
			
			Log.e("NavitDrawObjects", "failed to add object to queue");
		}
	}
	

	/** This function draws a polyline to the private bitmap.
	 *
	 * Each thread uses its own private paint and path object to draw.
	 * Otherwise the threads could not draw at the same time.
	 *
	 * Input array structure:
	 * 
	 * c[0] = stroke width
	 * c[1]-c[4] = ARGB color
	 * c[5] = ndashes
	 * c[6]-c[6+ndashes-1] = dash interval
	 * c[6+ndashes] - c[n] = coords for the polyline
	 *
	 * @param paint		Paint object ( not in use )
	 * @param c			Input array with some config and coords, see function description for more details.
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	private void draw_polyline(Paint p, int c[])
	{
		int i, ndashes;
		float [] intervals;
		
		paint_polyline.setStrokeWidth(c[0]);
		paint_polyline.setARGB(c[1],c[2],c[3],c[4]);
		

		
		
		ndashes=c[5];
		intervals=new float[ndashes+(ndashes%2)];
		for (i = 0; i < ndashes; i++)
			intervals[i]=c[6+i];

		if((ndashes%2)==1)
			intervals[ndashes]=intervals[ndashes-1];
			
		if(ndashes>0)
			paint_polyline.setPathEffect(new android.graphics.DashPathEffect(intervals,0.0f));
			
		
		path.moveTo(c[6+ndashes], c[7+ndashes]);
		for (i = 8+ndashes; i < c.length; i += 2)
		{
			path.lineTo(c[i], c[i + 1]);
		}
		
		priv_canvas[idx].drawPath(path, paint_polyline);
				
		paint_polyline.setPathEffect(null);
		
		path.rewind(); // uses structures again. reset() is slower
	}

	/** This function draws a polygon to the private bitmap.
	 *
	 * Each thread uses its own private paint and path object to draw.
	 * Otherwise the threads could not draw at the same time.
	 * 
	 * Input array structure:
	 * 
	 * c[0] = stroke width
	 * c[1]-c[4] = ARGB color
	 * c[5] - c[n] = coords for the polygon
	 *
	 * @param paint		Paint object ( not in use )
	 * @param c			Input array with some config and coords, see function description for more details.
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void draw_polygon(Paint p, int c[])
	{
		paint_polygon.setStrokeWidth(c[0]);
		paint_polygon.setARGB(c[1],c[2],c[3],c[4]);
		

		
		path.moveTo(c[5], c[6]);
		for (int i = 7; i < c.length; i += 2)
		{
			path.lineTo(c[i], c[i + 1]);
		}
		
		priv_canvas[idx].drawPath(path, paint_polygon);
		path.rewind();
	}

	/** This function draws a circle to the private bitmap.
	 *
	 * Each thread uses its own private paint object to draw.
	 * Otherwise the threads could not draw at the same time.
	 * 
	 * @param paint		Paint object ( not in use )
	 * @param x			first x coord of the rectangle
	 * @param y			first y coord of the rectangle
	 * @param r			radius of the circle
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void draw_circle(Paint p, int x, int y, int r)
	{
		paint.setStyle(Paint.Style.STROKE);
		priv_canvas[idx].drawCircle(x, y, r / 2, paint);
	}
	
	
	/** This function draws a text to the private bitmap.
	 *
	 * Each thread uses its own private paint object to draw.
	 * Otherwise the threads could not draw at the same time.
	 * 
	 * @param x			specifying the x text position
	 * @param y			specifying the y text position
	 * @param text		Text to draw
	 * @param size		specifying the size of the text
	 * @param dx		specifying the dx position, if text is drawn to a line
	 * @param dy		specifying the dy position, if text is drawn to a line
	 * @param bgcolor	specifying the background color
	 * @param lw		specifying the stroke width
	 * @param fgcolor	specifying the color of the text
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/	
	public void draw_text(int x, int y, String text, int size, int dx, int dy, int bgcolor, int lw, int fgcolor)
	{
		
		paint_text.setStrokeWidth(lw);
		
		Path path=null;
	
		paint_text.setTextSize(size / 15);
		paint_text.setStyle(Paint.Style.FILL);

		if (dx != 0x10000 || dy != 0) {
			path = new Path();
			path.moveTo(x, y);
			path.rLineTo(dx, dy);
			paint_text.setTextAlign(android.graphics.Paint.Align.LEFT);
		}

		if(bgcolor!=0) {
			paint_text.setStrokeWidth(3);
			paint_text.setColor(bgcolor);
			paint_text.setStyle(Paint.Style.STROKE);
			if(path==null) {
				priv_canvas[idx].drawText(text, x, y, paint_text);
			} else {
				priv_canvas[idx].drawTextOnPath(text, path, 0, 0, paint_text);
			}
			paint_text.setStyle(Paint.Style.FILL);
			
		}

		paint_text.setColor(fgcolor);
		
		if(path==null) {
			priv_canvas[idx].drawText(text, x, y, paint_text);
		} else {
			priv_canvas[idx].drawTextOnPath(text, path, 0, 0, paint_text);
		}
		paint_text.clearShadowLayer();
	}
	
	/** This function draws a text to the private bitmap.
	 *
	 * Each thread uses its own private paint object to draw.
	 * Otherwise the threads could not draw at the same time.
	 * 
	 * @param paint		Paint object ( not in use )
	 * @param x			specifying the x position the image is drawn to
	 * @param y			specifying the y position the image is drawn to
	 * @param bitmap	Bitmap object holding the image to draw
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/	
	public void draw_image(Paint p, int x, int y, Bitmap bitmap)
	{

		priv_canvas[idx].drawBitmap(bitmap, x, y, paint);
	}
	
	
}