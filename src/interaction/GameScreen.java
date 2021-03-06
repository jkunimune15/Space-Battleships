/**
 * Copyright (c) 2016 Kunimune
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package interaction;

import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.AlphaComposite;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import mechanics.Battlefield;
import mechanics.Body;
import mechanics.Order;
import mechanics.Ship;
import network.Protocol;

/**
 * The JPanel that displays the gameplay objects and HUD during a match.
 * 
 * @author jkunimune
 * @version 1.0
 */
public class GameScreen extends JPanel {

	private static final long serialVersionUID = -4461350953048532763L;
	
	private static final double MIN_SCALE = Math.exp(-1);	// the limits of the zooming
	private static final double MAX_SCALE = Math.exp(1);
	
	private static final Color MSG_COLOR = new Color(250, 245, 250);
	private static final Font MSG_FONT = new Font("Comic Sans MS", Font.ITALIC, 64);
	
	private static final int SHIP_SPACING = 46;	// a little thing that handles ship placement
	
	
	private HashMap<String, BufferedImage> sprites;	// the images it uses to display objects
	private HashMap<String, BufferedImage> icons;
	private HashMap<String, AudioClip> sounds;
	
	private Main application;
	private Battlefield game;
	private Controller listener;
	private Ship activeShip;
	
	private Canvas canvs;			// some necessary java.awt stuff
	private BufferStrategy strat;
	
	private int origX, origY;
	private double scale;	// the variables that define the screen's position and zoom-level
	private byte flip;		// either 1 or negative 1, gets multiplied to all space positions and rotations
	
	private HashMap<Ship, Point> shipLocations;	// the last drawn positions of the ships
	
	
	
	public GameScreen(int w, int h, Battlefield field, Main app, boolean host) {
		super();
		
		canvs = new Canvas();
		application = app;
		game = field;
		
		super.add(canvs);
		super.setPreferredSize(new Dimension(w,h));
		super.setLayout(null);
		super.setOpaque(true);
		super.setFocusable(true);
		
		canvs.setBounds(0,0,w,h);
		canvs.setIgnoreRepaint(true);
		canvs.setFocusable(true);
		
		addListener(new ShipPlacer(this, field));
		
		loadImages();
		loadSounds();
		
		origX = w/2;
		origY = h/2;
		scale = 1.0;
		shipLocations = new HashMap<Ship, Point>();
		if (host)	flip = 1;
		else		flip = -1;
	}
	
	
	
	public void developStrategy() {	// some required stuff for graphics to not fail
		canvs.createBufferStrategy(2);
		strat = canvs.getBufferStrategy();
		assert strat != null: "canvs.getBufferStrategy() returned null";
	}
	
	
	@Override
	public void setVisible(boolean v) {	// draws all things and plays sounds and displays itself
		if (strat == null)	// if we haven't finished our initialization yet (something about a valid peer?)
			return;			// skip ahead
		
		if (game.started && listener instanceof ShipPlacer) {	// start the game if you haven't yet
			addListener(new Controller(this, game));
			game.message = "";
		}
		
		game.update();			// start by updating the game model
		if (!game.active()) {
			application.goToMenu();
			return;				// and quitting if the game is over
		}
		
		final Graphics2D g = (Graphics2D)strat.getDrawGraphics();	// get some useful objects
		final double t = (double)System.currentTimeMillis();
		
		g.drawImage(icons.get("space"), 0, 0, null);	// draw the background
		
		final List<Body> bodies = game.getBodies();
		for (int i = 0; i < bodies.size(); i ++)	// for each Body
			draw(bodies.get(i), g, t);				// display its sprite
		drawIcons(g, t);
		
		if (game.started)
			drawHUD(g, t);		// draw the heads-up display
		else
			drawPregame(g);		// or the pre-game HUD
		
		drawMessage(g, t);		// display a message if there is one
		
		g.dispose();
		strat.show();
		super.setVisible(v);	// and finish up
	}
	
	
	private void draw(Body b, Graphics2D g, double t) {	// put a picture of b on g at time t
		t = game.observedTime(b, t);	// correct for information delay
		
		try {
			sounds.get(b.soundName(t)).play();	// now, play its sound
		} catch (NullPointerException e) {}		// if it has one
		
		if ((b instanceof Ship && ((Ship) b).isBlue()) || !b.existsAt(t))	// and skip drawing it if it does not exist or is a Ship
			return;
		
		BufferedImage img = getSprite(b.spriteName());	// finds the correct sprite
		
		try {
			img = executeTransformation(g, img, b.spriteTransform(t));	// does any necessary transformations
		}
		catch (java.awt.image.RasterFormatException e) { return; }	// if there's a problem with the transformation (probably roundoff), just skip it
		catch (java.awt.image.ImagingOpException e) { return; }	// I don't know the difference between these two exceptions
		
		int screenX = screenXFspaceX(b.xValAt(t));	// gets coordinates of b,
		int screenY = screenYFspaceY(b.yValAt(t));	// and offsets appropriately
		g.drawImage(img, screenX - img.getWidth()/2, screenY - img.getHeight()/2, null);
	}
	
	
	private void drawIcons(Graphics2D g, final double t0) {	// draw the ships and orders
		final List<Ship> ships = game.getShips();
		
		if (!game.started) {	// draw the placement region first
			BufferedImage reg = getSprite(game.getRegion().spriteName());	// finds the correct sprite
			reg = executeTransformation(g, reg, null);	// does any necessary transformations
			int screenX = screenXFspaceX(game.getRegion().xValAt(0));	// gets coordinates of b,
			int screenY = screenYFspaceY(game.getRegion().yValAt(0));	// and offsets appropriately
			g.drawImage(reg, screenX - reg.getWidth()/2, screenY - reg.getHeight()/2, null);
		}
		
		for (Ship s: ships) {
			final double ts = game.observedTime(s, t0);	// get the observed time
			if (!s.existsAt(ts))	continue;
			int screenX = screenXFspaceX(s.xValAt(ts));	// gets coordinates of b,
			int screenY = screenYFspaceY(s.yValAt(ts));	// and offsets appropriately
			shipLocations.put(s, new Point((int)screenX, (int)screenY));
			
			for (Order o: game.getOrders()) {	// draws orders
				final double to = game.observedTime(o, t0);
				if (o.existsAt(to) && o.getShip() == s.getID()) {
					final double r = game.dist(s, o, ts, to) - o.rValAt(to);
					BufferedImage ord = getSprite("order"+o.getType());
					try {
						BufferedImage mod = ord.getSubimage(0, 0,
								ord.getWidth()/2+(int)(r/scale), ord.getHeight());
						AffineTransform at = new AffineTransform();
						at.rotate(Math.atan2(flip*(o.yValAt(to)-s.yValAt(ts)), flip*(o.xValAt(to)-s.xValAt(ts))),
								ord.getWidth()/2, ord.getHeight()/2);
						mod = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)
							.filter(mod, null);
						g.drawImage(mod, screenX - ord.getWidth()/2, screenY - ord.getHeight()/2, null);
					} catch(java.awt.image.RasterFormatException e) {}
				}
			}
		}
		
		for (Ship s: ships) {
			if (!s.existsAt(game.observedTime(s, t0)))	continue;			// and skip dead ships
			
			BufferedImage img;
			if (game.started && s.getID() == listener.getShip())
				img = getSprite(s.spriteName()+"i");	// active ships have a special sprite
			else
				img = getSprite(s.spriteName());	// finds the correct sprite
			
			
			Point screenPos = shipLocations.get(s);
			g.drawImage(img, screenPos.x - img.getWidth()/2, screenPos.y - img.getHeight()/2, null);
		}
	}
	
	
	private void drawPregame(Graphics2D g) {	// draw the ships being placed
		g.drawImage(icons.get("selection_basin"), 0, 0, null);		// start with some background
		final int xc = icons.get("selection_basin").getWidth()/2;
		int pos = SHIP_SPACING/2 + 6;
		for (byte type: Ship.ALL_TYPES) {
			if (((ShipPlacer) listener).isAvailable(type)) {
				final BufferedImage img = getSprite(Ship.shipSprite(type));	// then draw each available ship
				g.drawImage(img, xc - img.getWidth()/2, pos - img.getHeight()/2, null);
			}
			pos += SHIP_SPACING;
		}
		
		if (((ShipPlacer) listener).getHeldShip() != -1) {	// then draw the ship in the user's hand
			final BufferedImage img = getSprite(Ship.shipSprite(((ShipPlacer) listener).getHeldShip()));
			g.drawImage(img, listener.getX()-img.getWidth()/2, listener.getY()-img.getHeight()/2, null);
		}
	}
	
	
	private void drawHUD(Graphics2D g, double t) {	// draw the heads up display
		final int pos = getMousePos(listener.getMouseLocation(), t);
		final int active = listener.getOrder();
		
		if (active == -2)	// the buttons may be lit, on, or off depending on circumstance
			g.drawImage(icons.get("button0_lt"), 0, 0, null);
		else if (pos == -2)
			g.drawImage(icons.get("button0_on"), 0, 0, null);
		else
			g.drawImage(icons.get("button0_of"), 0, 0, null);
		
		if (active == -3)
			g.drawImage(icons.get("button1_lt"), 0, this.getHeight()-400, null);
		else if (pos == -3)
			g.drawImage(icons.get("button1_on"), 0, this.getHeight()-400, null);
		else
			g.drawImage(icons.get("button1_of"), 0, this.getHeight()-400, null);
		
		if (active == -4)
			g.drawImage(icons.get("button2_lt"), this.getWidth()-200, this.getHeight()-400, null);
		else if (pos == -4)
			g.drawImage(icons.get("button2_on"), this.getWidth()-200, this.getHeight()-400, null);
		else
			g.drawImage(icons.get("button2_of"), this.getWidth()-200, this.getHeight()-400, null);
		
		if (activeShip == null)		return;	// if there's no selected ship, that's the end of it
		try {								// otherwise, draw more HUD
			if (game.started)	// adjust for information delay
				t = game.observedTime(activeShip, t);
			if (!activeShip.existsAt(t))	return;	// if the ship is dead, don't show the HUD
			
			final Point hudPos = new Point(1050, 30);
			g.drawImage(icons.get("bars"), hudPos.x, hudPos.y, null);	// draw more HUD stuff
			
			AffineTransform at;		// these classes help with the HP/PP bars
			AffineTransformOp op;
			BufferedImage mask;
			
			final double hTheta = Math.PI/2 - activeShip.hValAt(t)/Ship.MAX_H_VALUE*Math.PI/2;
			at = new AffineTransform();			// start with an AffineTransform
			at.rotate(hTheta, 200, 200);		// set it to rotate based on health
			op = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
			mask = op.filter(icons.get("health_mask"), null).getSubimage(200,0,200,200);	// now rotate the mask and draw it over the bars
			g.drawImage(mask, hudPos.x, hudPos.y, null);
			
			final double eTheta = -Math.PI/2 + activeShip.eValAt(t)/Ship.MAX_E_VALUE*Math.PI/2;
			at = new AffineTransform();			// repeat for energy bar
			at.rotate(eTheta, 0, 200);
			op = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
			mask = op.filter(icons.get("energy_mask"), null).getSubimage(0, 0, 200, 200);
			g.drawImage(mask, hudPos.x, hudPos.y, null);
		} catch (NullPointerException e) {}	// it might throw a NullPointerException if listener modifies activeShip at the wrong moment
	}
	
	
	private void drawMessage(Graphics2D g, double t) {
		if (game.message.isEmpty())	// if there is no message,
			return;						// don't do anything
		
		final BufferedImage img = icons.get("popup");
		final int xi = this.getWidth()/2 - img.getWidth()/2;
		final int yi = this.getHeight()/2 - img.getHeight()/2;
		g.drawImage(img, xi, yi, null);
		g.setColor(MSG_COLOR);
		g.setFont(MSG_FONT);
		final int xs = this.getWidth()/2 - g.getFontMetrics().stringWidth(game.message)/2;
		final int ys = this.getHeight()/2 + g.getFontMetrics().getHeight()/4;
		g.drawString(game.message, xs, ys);
	}
	
	
	private BufferedImage executeTransformation(Graphics2D g, BufferedImage img,
											double[] params) {	// rotozooms img based on params		
		AffineTransform at = new AffineTransform();
		if (params == null)		params = Body.DEFAULT_TRANSFORM;
		
		at.scale(params[1]/scale, params[2]/scale);					// scales (if necessary)
		
		if (params[0] != 0.0) {
			if (flip > 0)
				at.rotate(params[0], img.getWidth()/2, img.getHeight()/2);		// rotates (if necessary)
			else
				at.rotate(params[0]+Math.PI, img.getWidth()/2, img.getHeight()/2);
		}
		
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) params[3]));	// fades
		
		AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		Point size = new Point((int) (img.getWidth()*params[1]/scale),	// this is how big it would be if it didn't pad with zeros
				(int) (img.getHeight()*params[2]/scale));
		return op.filter(img, null).getSubimage(0, 0, size.x, size.y);	// executes affine transformation, crops, and returns
	}
	
	
	private void loadImages() {	// reads all images in the assets directory and saves them in a HashMap
		sprites = new HashMap<String, BufferedImage>();
		icons = new HashMap<String, BufferedImage>();
		
		File[] files = new File("assets/images/game").listFiles();
		for (File f: files) {	// for every file in that folder
			try {
				int i = f.getName().lastIndexOf('.');	// find the extension
				if (i != -1 && f.getName().endsWith(".png"))	// if it is a png file
					sprites.put(f.getName().substring(0,i), ImageIO.read(f));	// put it in the HashMap
			} catch (IOException e) {
				System.err.println("Something went wrong with "+f);	// I see no reason this error would ever throw
			}
		}
		
		files = new File("assets/images/interface").listFiles();	// do the same for HUD pics now
		for (File f: files) {	// for every file in that folder
			try {
				int i = f.getName().lastIndexOf('.');	// find the extension
				if (i != -1 && f.getName().endsWith(".png"))	// if it is a png file
					icons.put(f.getName().substring(0,i), ImageIO.read(f));	// put it in the HashMap
			} catch (IOException e) {
				System.err.println("Something went wrong with "+f);	// I see no reason this error would ever throw
			}
		}
	}
	
	
	private void loadSounds() {
		sounds = new HashMap<String, AudioClip>();
		
		File[] files = new File("assets/sounds").listFiles();
		for (File f: files) {	// for every file in that folder
			try {
				int i = f.getName().lastIndexOf('.');	// find the extension
				if (i != -1 && f.getName().endsWith(".wav"))	// if it is a wav file
					sounds.put(f.getName().substring(0,i), Applet.newAudioClip(f.toURI().toURL()));	// put it in the HashMap
			} catch (IOException e) {
				System.err.println("Something went wrong with "+f);	// I see no reason this error would ever throw
			}
		}
	}
	
	
	public void beReady() {	// exit pre-game and prepare for the real battle
		game.receive(Protocol.announceReadiness());
		game.message = "Waiting for players...";
	}
	
	
	public void addListener(Controller c) {
		if (listener != null) {
			canvs.removeMouseListener(listener);
			canvs.removeMouseWheelListener(listener);
			canvs.removeMouseMotionListener(listener);
			canvs.removeKeyListener(listener);
		}
		
		canvs.addMouseListener(c);
		canvs.addMouseWheelListener(c);
		canvs.addMouseMotionListener(c);
		canvs.addKeyListener(c);
		
		listener = c;
	}
	
	
	public void zoom(int amount, int mx, int my) {	// changes scale based on a multiplicative amount
		final double expAmount = Math.exp(amount/5.0);
		if (amount > 0 && scale*expAmount >= MAX_SCALE)	// first check that you aren't out of bounds
			return;
		if (amount < 0 && scale*expAmount <= MIN_SCALE)
			return;
		
		scale *= expAmount;		// then reset scale
		
		origX = (int) Math.round((origX-mx)/expAmount) + mx;	// then alter origX and origY
		origY = (int) Math.round((origY-my)/expAmount) + my;
	}
	
	
	public void pan(int delX, int delY) {	// changes offsetX and offsetY based on a mouse drag
		origX += delX;
		origY += delY;
	}
	
	
	public byte getMousePos(Point mCoords, double t) {	// decides which, if any, button/object the mouse is currently on
		return getMousePos(mCoords.x, mCoords.y, t);
	}
	
	
	public byte getMousePos(int x, int y) {	// decides which, if any, button/object the mosue is currently on
		return getMousePosPreGame(x, y);
	}
	
	
	public byte getMousePos(int x, int y, double t) {	// decides which, if any, button/object the mouse is currently on
		if (game.started)	return getMousePosInGame(x, y, t);
		else				return getMousePosPreGame(x, y);
	}
	
	
	public byte getMousePosPreGame(int x, int y) {	// decides which, if any, button/object the mouse is currently one
		if (x > icons.get("selection_basin").getWidth()) {
			if (!game.getRegion().contains(spaceXFscreenX(x), spaceYFscreenY(y)))
				return -3;				// invalid space
			else
				return -2;				// valid space
		}
		final int i = (y-6)/SHIP_SPACING;
		if (i < Ship.ALL_TYPES.length)
			return Ship.ALL_TYPES[i];	// some kind of ship
		else
			return -1;					// selection basin
	}
	
	
	public byte getMousePosInGame(int x, int y, double t) {	// decides which, if any, button/object the mouse is currently on
		if (x < 200   && y < 400)	return -2;	// move button
		if (x < 200   && y >= 400)	return -3;	// shoot button
		if (x >= 1080 && y >= 400)	return -4;	// special button
		final List<Ship> ships = game.getShips();
		for (int i = ships.size()-1; i >= 0; i --) {
			final Ship s = ships.get(i);
			if (s.isBlue()) {
				final Point p = shipLocations.get(s);
				if (p != null)
					if (Math.hypot(x - p.x, y - p.y) < 20)
						return s.getID();	// a ship
			}
		}
		return -1;	// empty space
	}
	
	
	public BufferedImage getSprite(String name) {
		if (sprites.containsKey(name))
			return sprites.get(name);
		else
			return sprites.get("null");
	}
	
	
	public final double spaceXFscreenX(int sx) {	// converts an x on screen to an x in space
		return flip*(sx-origX)*scale;
		
	}
	
	
	public final double spaceYFscreenY(int sy) {	// converts a y on screen to a y in space
		return flip*(sy-origY)*scale;
	}
	
	
	public final int screenXFspaceX(double sx) {	// converts an x in space to an x on screen
		return (int)(flip*sx/scale) + origX;
	}
	
	
	public final int screenYFspaceY(double sy) {	// converts a y in space to a y on screen
		return (int)(flip*sy/scale) + origY;
	}
	
	
	public boolean setShip(byte id) {			// sets the activeShip field to the one with the matching id
		activeShip = game.getShipByID(id);
		return activeShip != null;	// returns whether that ship exists
	}

}
