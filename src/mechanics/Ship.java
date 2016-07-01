/**
 * An object that can move around and produce lasers on command from the player or the client.
 */
package mechanics;

import java.util.ArrayList;

/**
 * @author jkunimune
 * @version 1.0
 */
public abstract class Ship extends Body {

	private boolean isBlue;	// whether it is blue or red
	
	protected byte id;		// an identifier for this particular ship
	
	protected ArrayList<double[]> health;
	protected ArrayList<double[]> energy;
	
	
	
	public Ship(double newX, double newY, double time, byte pin, boolean blue, Battlefield space) {
		super(newX, newY, 0, 0, time, space);
		id = pin;
		isBlue = blue;
		
		double[] hInit = {time, 1.5*Univ.MJ};
		double[] eInit = {time, 5*Univ.MJ};
		health = new ArrayList<double[]>(1);
		energy = new ArrayList<double[]>(1);
		health.add(hInit);
		energy.add(eInit);
		
		scales = false;	// ships don't scale because they're sprites are icons
	}
	
	
	
	@Override
	public String spriteName() {
		if (isBlue)	return "_b";
		else		return "_r";
	}
	
	
	@Override
	public boolean existsAt(double t) {
		return super.existsAt(t) && hValAt(t) > 0;
	}
	
	
	public void damaged(double amount, double t) {	// take some out of your health
		double[] newHVal = {t, hValAt(t)-amount};
		health.add(newHVal);
		if (newHVal[1] <= 0) {
			clearSoundsAfter(t);	// if the ship just died
			playSound("boom"+(int)(Math.random()*2.001), t);	// cancel any later sounds with a boom
		}
	}
	
	
	public void shoot(double x, double y, double t) {	// shoot a 1 megajoule laser at time t
		final double theta = Math.atan2(y-yValAt(t),x-xValAt(t));
		final double nrg = 1*Univ.MJ;
		final double spawnDist = Laser.rValFor(nrg) + 1*Univ.m;	// make sure you spawn it in front of the ship so it doesn't shoot itself
		space.spawn(new Laser(xValAt(t) + spawnDist*Math.cos(theta),
							  yValAt(t) + spawnDist*Math.sin(theta),
							  theta, t, space, nrg));
		playSound("pew", t);	// play the pew pew sound
	}
	
	
	public void move(double x, double y, double t) {	// move to the point x,y at a speed of c/10
		for (int i = pos.size()-1; i >= 0; i --) {	// first, clear any movement after this order
			if (pos.get(i)[0] >= t)	pos.remove(i);
			else					break;
		}
		final double x0 = xValAt(t);	// calculate the initial coordinates
		final double y0 = yValAt(t);
		final double delT = Math.hypot(x-x0, y-y0)/(Univ.c/10);	// the duration of the trip
		
		double[] newPos = {t, x0, y0, (x-x0)/delT, (y-y0)/delT};	// add a segment for the motion
		pos.add(newPos);
		double[] newnewPos = {t+delT, x, y, 0, 0};	// and have it stop afterward
		pos.add(newnewPos);
		
		playSound("blast", t);			// then make it play the blast sound at the beginning and end
		clearSoundsAfter(t);
		playSound("blast", t+delT);
	}
	
	
	public abstract void special(double x, double y, double t);
	
	
	public double hValAt(double t) {	// the health at time t
		for (int i = health.size()-1; i >= 0; i --) {	// iterate through health to find the correct motion segment
			final double[] timehealth = health.get(i);
			if (timehealth[0] <= t) {	// they should be sorted chronologically
				return timehealth[1];	// calculate health based on this
			}
		}
		return health.get(0)[1];	// if it didn't find anything, just use the first one
	}
	
	
	public byte getID() {
		return id;
	}

}
