package com.astar.i2r.ins.localization;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.log4j.Logger;

import com.astar.i2r.ins.data.BaroData;
import com.astar.i2r.ins.data.CANData;
import com.astar.i2r.ins.data.CompassData;
import com.astar.i2r.ins.data.Data;
import com.astar.i2r.ins.data.GPSData;
import com.astar.i2r.ins.data.MotionData;
import com.astar.i2r.ins.map.CarParkDB;
import com.astar.i2r.ins.map.GeoMap;
import com.astar.i2r.ins.motion.Attitude;
import com.astar.i2r.ins.motion.GeoPoint;
import com.astar.i2r.ins.motion.Speed;
import com.astar.i2r.ins.motion.Step;
import com.astar.i2r.ins.motion.Velocity;

class Vehicle implements Context {

	private static final Logger log = Logger.getLogger(Vehicle.class.getName());

	private static double GPSCALIBACCURACYTHRESHOLD = 10;
	private static double GPSCALIBDISTANCETHRESHOLD = 10;

	private static double GPSOKTHRESHOLD = 15;

	private Attitude attitude = null;
	private Speed speed = null;
	private double compassHeading = Double.NaN;
	private Data curData = null;
	private GeoPoint curPos = null;
	private GeoPoint lastAccGPS = null;
	private Vector3D GPSCalibVector = null;
	// private double yawCalib = Double.NaN;
	private double yawCalib = 2.32;
	private boolean GPSOK = false;
	private Step step = null;
	private double baroAltitude = Double.NaN;
	private double lpBaro = Double.NaN;

	private State state = NavigationState.SLAM;

	@Override
	public State state() {
		return state;
	}

	@Override
	public void incoming(Data data) {
		curData = data;
	}

	// set the next state of a vehicle
	@Override
	public void state(State nextState) {

		if (state == NavigationState.GPS && nextState == NavigationState.SLAM) {
			step = new Step(0, 0, 0, curData.time);
		} else if (state == NavigationState.SLAM
				&& nextState == NavigationState.GPS) {
			step = null;
			lpBaro = baroAltitude;
		}

		state = nextState;

		log.info("Switch into " + state.name() + " at "
				+ new Date(curData.time).toString());
	}

	@Override
	public boolean isGPSOK() {
		if (curData instanceof GPSData) {
			GPSData data = ((GPSData) curData);

			double accuracy = 0;
			accuracy += data.accuracy[0] * data.accuracy[0];
			accuracy += data.accuracy[1] * data.accuracy[1];
			accuracy = Math.sqrt(accuracy);

			if (accuracy > GPSOKTHRESHOLD) {
				GPSOK = false;
			} else {
				GPSOK = true;
			}

//			if (curPos != null) {
//				if (CarParkDB.isInBuilding(curPos.lat, curPos.lon)) {
//					GPSOK = false;
//				}
//			}

		}
		return GPSOK;
	}

	@Override
	public boolean SLAMUpdate() {

		if (curData instanceof CANData) {
			SLAMUpdate(((CANData) curData));
		} else if (curData instanceof MotionData) {
			SLAMUpdate(((MotionData) curData));
		} else if (curData instanceof CompassData) {
			SLAMUpdate(((CompassData) curData));
		} else if (curData instanceof BaroData) {
			SLAMUpdate(((BaroData) curData));
		} else if (curData instanceof GPSData) {
			SLAMUpdate(((GPSData) curData));
		}

		return true;
	}

	private void SLAMUpdate(CANData data) {
		speed = new Speed(data.vehSpdkmh / 3.6, data.time);

		if (attitude == null) {
			return;
		}

		Vector3D vel = attitude.getVelocity(speed.speedms);
		Velocity approxVel = new Velocity(vel, data.time);
		step = step.increment(approxVel);

		if (curPos == null) {
			return;
		}

		if (step.getNorm() > Step.MINSTEP) {
			log.trace("Step: " + step.toString() + " GPS: " + curPos.toString());
			curPos = curPos.add(step);
			step = new Step(0, 0, 0, data.time);
		}
	}

	private void SLAMUpdate(MotionData data) {

		attitude = new Attitude(data.cardan, yawCalib, data.time);

		if (speed == null) {
			return;
		}

		Vector3D vel = attitude.getVelocity(speed.speedms);
		Velocity approxVel = new Velocity(vel, data.time);
		step = step.increment(approxVel);

		if (curPos == null) {
			return;
		}

		if (step.getNorm() > Step.MINSTEP) {
			log.trace("Step: " + step.toString() + " GPS: " + curPos.toString());
			curPos = curPos.add(step);
			step = new Step(0, 0, 0, data.time);
		}
	}

	private void SLAMUpdate(CompassData data) {
		compassHeading = data.rotationHeading;
	}

	private void SLAMUpdate(GPSData data) {
		// GPS data in SLAM mode?
	}

	private void SLAMUpdate(BaroData data) {
		baroAltitude = data.altitude;
	}

	@Override
	public boolean localize() {

		return false;
	}

	private boolean isGPSAccurate(GPSData data) {
		double accuracy = 0;
		accuracy += data.accuracy[0] * data.accuracy[0];
		accuracy += data.accuracy[1] * data.accuracy[1];
		accuracy = Math.sqrt(accuracy);
		if (accuracy > GPSCALIBACCURACYTHRESHOLD) {
			return false;
		} else {
			return true;
		}
	}

	@Override
	public boolean GPSUpdate() {

		if (curData instanceof BaroData) {
			GPSUpdate((BaroData) curData);
		} else if (curData instanceof GPSData) {
			GPSUpdate((GPSData) curData);
		} else if (curData instanceof CANData) {
			GPSUpdate((CANData) curData);
		} else if (curData instanceof MotionData) {
			GPSUpdate((MotionData) curData);
		} else if (curData instanceof CompassData) {
			GPSUpdate((CompassData) curData);
		}

		return true;
	}

	private void GPSUpdate(BaroData data) {
		baroAltitude = data.altitude;
		if (Double.isNaN(lpBaro)) {
			lpBaro = baroAltitude;
		} else {
			double ratio = 49.0 / 50;
			lpBaro = lpBaro * ratio + baroAltitude * (1 - ratio);
		}
	}

	private void GPSUpdate(CANData data) {

	}

	private void GPSUpdate(MotionData data) {

		if (GPSCalibVector != null) {
			double newCalib = getGPSCalibFactor(GPSCalibVector, data.cardan[2]);

			if (Double.isNaN(yawCalib)) {
				yawCalib = newCalib;
			} else {
				yawCalib = angleAverage(yawCalib, newCalib, 3.0 / 4);
			}

			GPSCalibVector = null;
			log.debug("GPS Heading Calibration Factor " + newCalib / Math.PI
					* 180 + "\t Averaged: " + yawCalib / Math.PI * 180);
		}

		attitude = new Attitude(data.cardan, yawCalib, data.time);
	}

	private void GPSUpdate(GPSData data) {

		curPos = new GeoPoint(data.gps[0], data.gps[1], 0, data.time);
		speed = new Speed(data.speedms, data.time);

		// generator calibration factor
		if (isGPSAccurate(data)) {

			GeoPoint curGPS = new GeoPoint(data.gps[0], data.gps[1], 0,
					data.time);

			if (lastAccGPS != null) {
				Vector3D v = GeoPoint.distance(lastAccGPS, curGPS);
				if (v.getNorm() > GPSCALIBDISTANCETHRESHOLD) {
					GPSCalibVector = v;
				} else {
					GPSCalibVector = null;
				}
			} else {
				GPSCalibVector = null;
			}
			lastAccGPS = curGPS;
		} else {
			GPSCalibVector = null;
			lastAccGPS = null;
		}

	}

	private void GPSUpdate(CompassData data) {

	}

	@Override
	public GeoPoint getGPS() {

		return curPos;
	}

	private double angleAverage(double a1, double a2, double rate) {

		a1 = a1 % (Math.PI * 2);
		a2 = a2 % (Math.PI * 2);
		if (Math.abs(a1 - a2) > Math.PI) {
			if (a1 > a2) {
				a2 = a2 + (Math.PI * 2);
			} else {
				a1 = a1 + (Math.PI * 2);
			}
		}

		double a = a1 * rate + a2 * (1 - rate);

		return a;
	}

	/**
	 * 
	 * @param gpsyaw
	 * @param attyaw
	 * @return
	 */
	private double getGPSCalibFactor(Vector3D v, double attyaw) {
		double gpsyaw = v.getAlpha() + Math.PI / 2;
		double newCalib = (gpsyaw - attyaw) % (Math.PI * 2);
		return newCalib;

	}

	@Override
	public double getRelativeAltitude() {
		if (state == NavigationState.GPS) {
			return 0;
		} else if (state == NavigationState.SLAM) {
			return baroAltitude - lpBaro;
		}
		return 0;
	}

}