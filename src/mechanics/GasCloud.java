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
package mechanics;

import java.util.ArrayList;

/**
 * A large body that absorbs and confounds lasers.
 * 
 * @author	jkunimune
 * @version	1.0
 */
public class GasCloud extends Body {

	public static final double SPEED = Univ.c/30;		// a speed related to the inital rate of expansion
	public static final double LIFETIME = 30*Univ.s;	// a time related to the total lifetime of a cloud
	
	public static final double HALF_LIFE = 5*Univ.s;	// the half-life of energy in this cloud
	public static final double LASER_ENERGY = 0.1*Univ.MJ;	// the energy required to form a laser
	
	
	protected ArrayList<double[]> energy;	// an ArrayList that keeps track of the ever-changing energy value
	
	
	
	public GasCloud(double x0, double y0, double vx0, double vy0, double time, Battlefield space) {
		super(x0, y0, vx0, vy0, time, space);
		
		energy = new ArrayList<double[]>(1);
		final double[] init = {time, 0, 0};	// zeroth value is time, first value is internal energy, and second
		energy.add(init);				// value is 'built up energy', a quantity which decides when to emit lasers
	}
	
	
	
	@Override
	public boolean existsAt(double t) {	// the gas cloud ceases to exist once it disperses enough
		return super.existsAt(t) && age(t) < LIFETIME;
	}
	
	
	@Override
	public void update(double t) {	// a GasCloud with energy will gradually release lasers
		while (EsValAt(t) >= LASER_ENERGY) {
			double tht = 2*Math.PI*Math.random();	// pick a random direction
			double r = this.rValAt(t) + Laser.rValFor(LASER_ENERGY);	// pick a safe distance
			space.spawn(new Laser(xValAt(t) + r*Math.cos(tht),		// fire it
								  yValAt(t) + r*Math.sin(tht),
								  tht, t, space, LASER_ENERGY));
			
			final double[] newE = {t, EValAt(t)-LASER_ENERGY, EsValAt(t)-LASER_ENERGY};
			energy.add(newE);	// WARNING: 
		}
	}
	
	
	@Override
	public void interactWith(Body that, double t) {	// GasClouds interact with lasers by absorbing them
		if (that instanceof Laser && !(that instanceof UVLaser) && space.dist(this, that, t) < rValAt(t)) {
			((Laser) that).collide(t);	// absorb the laser

			final double[] newE = {t, this.EValAt(t)+((Laser) that).EVal(), this.EsValAt(t)};
			energy.add(newE);	// and increase your energy
		}
	}
	
	
	@Override
	public String spriteName() {
		return "gas";
	}
	
	
	@Override
	public double[] spriteTransform(double t) {
		final double r = rValAt(t);
		final double a = Math.max(0, 1 - Math.pow(age(t)/LIFETIME, 2));
		final double[] res = {0, r/100.0,r/100.0, a};	// 150 is the sprite radius, so dividing by 100 draws it a little bigger
		return res;
	}
	
	
	public double rValAt(double t) {	// this equation is the result of assuming a density that decays
		return Math.max(0, -SPEED*age(t)*Math.log(age(t)/LIFETIME));	// exponentially with distance and gas
	}											// particles that do not interact with each other
	
	
	public double EValAt(double t) {	// the energy stored in the gas cloud
		for (int i = energy.size()-1; i >= 0; i --) {
			if (energy.get(i)[0] <= t)
				return energy.get(i)[1];
		}
		return 0;
	}
	
	
	public double EsValAt(double t) {	// the integral of the energy stored in the gas coud
		for (int i = energy.size()-1; i >= 0; i --) {
			final double[] state = energy.get(i);
			if (state[0] <= t)
				return Math.max(0, state[2] + state[1]*(t-state[0])/HALF_LIFE);
		}
		return 0;
	}

}
