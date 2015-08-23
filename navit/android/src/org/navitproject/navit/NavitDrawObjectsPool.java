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
import java.util.List;
import java.util.ArrayList;

/** 
 *
 * @brief This class manages the drawing threads and assigns the incoming objects to one of the threads.
 * 
 * @author Sascha Oedekoven (07/2015)
 */

public class NavitDrawObjectsPool 
{
	
	/** \brief A counter to ensure an alternately object distribution to the drawing threads*/
	private int next_thread;
	
	/** \brief Reference to the NavitGraphics object. Needed in NavitDrawObjects.*/
	private NavitGraphics ng;
		
	/** \brief List to access the drawing threads*/
	private List<NavitDrawObjects> drawThreads;
	
	/** \brief Number of drawing threads.*/
	public static int thread_n;
	
	/** \brief Width of the bitmap (screen).*/
	private int w;
	/** \brief Height of the bitmap (screen).*/
	private int h;
	
	/** Constructor, sets the number of drawing threads.
	 *
	 *
	 * @param navitgraphics	A reference to the NavitGraphics object.
	 * @param thread_count	The number of drawing threads.
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/	
	public NavitDrawObjectsPool(NavitGraphics navitgraphics, int thread_count) {
		next_thread = 0;
		ng = navitgraphics;
		thread_n = thread_count;
		drawThreads = new ArrayList<NavitDrawObjects>();
	}
	
	/** Function to set the height and width of the map.
	 *
	 *
	 * @param width		Width of the map (screen)
	 * @param height	Height of the map (screen)
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void init(int width, int height) {
		
		w = width;
		h = height;
		
	}
	
	/** This function starts the drawing threads.
	 *
	 * If the number of threads differs from the thread_n, then the old threads are canceled and new threads are started.
	 *
	 * @author Sascha Oedekoven (08/2015)
	 **/
	private void startThreads() {
		
		if(drawThreads.size() != thread_n ) {
			//stop all threads and start new!
			
			for(int i = 0; i < drawThreads.size(); i++) {
				drawThreads.get(i).run = false;
				drawThreads.get(i).cancel_draw();
			}
			drawThreads.clear();
			
			for(int i =0; i < thread_n; i++) {
				NavitDrawObjects d = new NavitDrawObjects(w, h, ng, (i+1));
				d.start();
				drawThreads.add(d);
			}	
			
		} 
	}
	
	
	/** This function stops the drawing process in all running threads.
	 *
	 * All running threads will be stopped immediately.
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void cancel_draw() {
		
		for(int i =0; i < drawThreads.size() ; i++) {
			drawThreads.get(i).cancel_draw();
		}
		
		drawThreads.clear();
		next_thread = 0;
		
		
	}
	
	/** This function tells all drawing threads to draw their private bitmap to the screen.
	 *
	 * Parameter mode set to 1, the private bitmap will be drawn to the screen.
	 * With the mode parameter set to 2, the current map will be saved to the cached_bitmap in NavitGraphics.
	 *
	 * This function also can be used to synchronize after each layer.
	 * But this is currently disabled, because it does not perform well.
	 *
	 * @param mode	1 - draw to screen, 2 - cache the current map
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void draw_to_screen(int mode) {
		
		if(drawThreads.size() == 0)
			return;
		
		NavitDrawObject obj = new NavitDrawObject();
		obj.type = NavitObjectType.TOSCREEN;
		obj.mode = mode;
		for(int i =thread_n-1; i >= 0 ; i--) {
			drawThreads.get(i).add_object(obj);
		}
		
		
	}
	
	/** This function adds a polyline to the thread save queue of one of the drawing threads.
	 *
	 * The drawing threads will get the objects alternately.
	 * 
	 * If no thread is running, the element will be ignored. (this should never happen)
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
	public void add_polyline(Paint paint, int c[]) {
		
		if(drawThreads.size() == 0)
			return;
		
		NavitDrawObject obj = new NavitDrawObject();
		
		obj.type = NavitObjectType.POLYLINE;
		obj.c = c;
		obj.paint = paint;
		
		drawThreads.get(next_thread++ % thread_n).add_object(obj);
		
		
	}
	
	/** This function adds a polygon to the thread save queue of one of the drawing threads.
	 *
	 * The drawing threads will get the objects alternately.
	 * 
	 * If no thread is running, the element will be ignored. (this should never happen)
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
	public void add_polygon(Paint paint, int c[]) {

		if(drawThreads.size() == 0)
			return;
		
		NavitDrawObject obj = new NavitDrawObject();
		
		obj.type = NavitObjectType.POLYGON;
		obj.c = c;
		obj.paint = paint;
		
		drawThreads.get(next_thread++ % thread_n).add_object(obj);
		
	}
	
	/** This function sets the number of drawing threads.
	 *
	 * After setting the number of threads, they will be started.
	 *
	 * @param n		Number of drawing threads
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void setThread_n(int n) {
		thread_n = n;
		startThreads();
		
	}

	/** This function adds a circle to the thread save queue of one of the drawing threads.
	 *
	 * The drawing threads will get the objects alternately.
	 * 
	 * If no thread is running, the element will be ignored. (this should never happen)
	 *
	 * @param paint		Paint object ( not in use )
	 * @param x			first x coord of the rectangle
	 * @param y			first y coord of the rectangle
	 * @param r			radius of the circle
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void add_circle(Paint paint, int x, int y, int r) {

		if(drawThreads.size() == 0)
			return;
	
		NavitDrawObject obj = new NavitDrawObject();
		
		obj.type = NavitObjectType.CIRCLE;
		obj.paint = paint;
		obj.x = x;
		obj.y = y;
		obj.r = r;
		
		drawThreads.get(next_thread++ % thread_n).add_object(obj);
	}
	
	/** This function adds a text to the thread save queue of one of the drawing threads.
	 *
	 * The drawing threads will get the objects alternately.
	 * 
	 * If no thread is running, the element will be ignored. (this should never happen)
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
	public void add_text(int x, int y, String text, int size, int dx, int dy, int bgcolor, int lw, int fgcolor) {

		if(drawThreads.size() == 0)
			return;
		
		NavitDrawObject obj = new NavitDrawObject();
		
		obj.type = NavitObjectType.TEXT;
		obj.x = x;
		obj.y = y;
		obj.text = text;
		obj.size = size;
		obj.dx = dx;
		obj.dy = dy;
		obj.bgcolor = bgcolor;
		obj.lw = lw;
		obj.fgcolor = fgcolor;
		
		drawThreads.get(next_thread++ % thread_n).add_object(obj);
	}
	
	/** This function adds a image to the thread save queue of one of the drawing threads.
	 *
	 * The drawing threads will get the objects alternately.
	 * 
	 * If no thread is running, the element will be ignored. (this should never happen)
	 *
	 * @param paint		Paint object ( not in use )
	 * @param x			specifying the x position the image is drawn to
	 * @param y			specifying the y position the image is drawn to
	 * @param bitmap	Bitmap object holding the image to draw
	 *
	 * 
	 * @author Sascha Oedekoven (08/2015)
	 **/
	public void add_image(Paint paint, int x, int y, Bitmap bitmap) {
		
		if(drawThreads.size() == 0)
			return;
		
		NavitDrawObject obj = new NavitDrawObject();
		
		obj.type = NavitObjectType.IMAGE;
		obj.paint = paint;
		obj.x = x;
		obj.y = y;
		obj.bitmap = bitmap;
		
		drawThreads.get(next_thread++ % thread_n).add_object(obj);
	}
	
	
}
