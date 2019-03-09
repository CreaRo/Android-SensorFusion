package rish.crearo.sensorfusionlib;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import rish.crearo.sensorfusionlib.listeners.FusionListener;
import rish.crearo.sensorfusionlib.listeners.VerboseFusionListener;

/**
 * Created by rish on 10/7/17.
 *
 * @author : bhardwaj.rish@gmail.com
 */

public class SensorFusion implements SensorEventListener {

    private static final String TAG = SensorFusion.class.getSimpleName();
    /* Nanoseconds to seconds */
    private static final float NS2S = 1.0f / 1000000000.0f;
    private SensorManager sensorManager;
    private Sensor sensorGyro, sensorAcc, sensorMag;
    private long prevGyroTimestamp = 0;

    /**
     * This is the absolute difference in sensor values from the previously recorded reading.
     * For gyroscope, it is simply the integration of angular velocity (angVel * dT)
     * For acc/mag, it is the difference in orientation angles (YPR) obtained using
     * SensorManager.getOrientation(rotationMatrix) between current and prev orientations
     **/
    private float gyroDiff[] = new float[3];
    private float accMagDiff[] = new float[3];

    /**
     * Using these to clone values received from SensorEventListener callback
     */
    private float accData[], magData[];

    /**
     * Stores YPR(in that order) of the fused magnetometer and accelerometer obtained by calling
     * SensorManager.getRotationMatrix(acc, mag), and then passing the rotation matrix received to
     * SensorManager.getOrientation(rot).
     */
    private float accMagPrevOrientation[];
    private float accMagOrientation[] = new float[3];

    /* temp variable storing rotation matrix */
    private float accMagRotationMatrix[] = new float[16];

    /* pitch, roll, yaw (x, y, z) */
    private float accMagTrajectory[] = new float[3];
    private float gyroTrajectoryCorrected[] = new float[3];
    private float gyroTrajectoryRaw[] = new float[3];
    private float fusedTrajectory[] = new float[3];

    private VerboseFusionListener verboseFusionListener;
    private FusionListener fusionListener;
    private float alpha = 0.995f;
    private float oneMinusAlpha = 1.0f - alpha;

    public SensorFusion(Context context, VerboseFusionListener verboseFusionListener) {
        initSensors(context);
        this.verboseFusionListener = verboseFusionListener;
    }

    public SensorFusion(Context context, FusionListener fusionListener) {
        initSensors(context);
        this.fusionListener = fusionListener;
    }

    private void initSensors(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void start() {
        sensorManager.registerListener(this, sensorGyro, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorAcc, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorMag, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == sensorGyro.getType()) {
            calculateRawGyroOrientation(event.values, event.timestamp);
            if (verboseFusionListener != null)
                verboseFusionListener.onGyroOrientation(gyroTrajectoryRaw, event.timestamp);
            calculateFusedOrientation();
            if (verboseFusionListener != null)
                verboseFusionListener.onFusedOrientation(fusedTrajectory, event.timestamp);
            if (fusionListener != null)
                fusionListener.onFusedOrientation(fusedTrajectory, event.timestamp);
        } else if (event.sensor.getType() == sensorMag.getType()) {
            if (magData == null) magData = new float[3];
            System.arraycopy(event.values, 0, magData, 0, event.values.length);
        } else if (event.sensor.getType() == sensorAcc.getType()) {
            if (accData == null) accData = new float[3];
            System.arraycopy(event.values, 0, accData, 0, event.values.length);
            calculateAccMagOrientation();
            if (verboseFusionListener != null)
                verboseFusionListener.onAccMagOrientation(accMagTrajectory, event.timestamp);
        }
    }

    private void calculateRawGyroOrientation(float angularVelocity[], long timestamp) {
        if (prevGyroTimestamp != 0) {
            float dt = (timestamp - prevGyroTimestamp) * NS2S;
            gyroDiff[0] = dt * angularVelocity[0];
            gyroDiff[1] = dt * angularVelocity[1];
            gyroDiff[2] = dt * angularVelocity[2];
        }
        prevGyroTimestamp = timestamp;

        /* Add these diff values to raw and corrected trajectory, just the same */
        gyroTrajectoryRaw[0] += gyroDiff[0];
        gyroTrajectoryRaw[1] += gyroDiff[1];
        gyroTrajectoryRaw[2] += gyroDiff[2];

        gyroTrajectoryCorrected[0] += gyroDiff[0];
        gyroTrajectoryCorrected[1] += gyroDiff[1];
        gyroTrajectoryCorrected[2] += gyroDiff[2];
    }

    private void calculateAccMagOrientation() {
        if (magData != null && accData != null) {
            if (SensorManager.getRotationMatrix(accMagRotationMatrix, null, accData, magData)) {
                SensorManager.getOrientation(accMagRotationMatrix, accMagOrientation);
                if (accMagPrevOrientation == null) {
                    accMagPrevOrientation = new float[3];
                    accMagDiff = new float[3];
                } else {
                    accMagDiff[0] = accMagOrientation[0] - accMagPrevOrientation[0];
                    accMagDiff[1] = accMagOrientation[1] - accMagPrevOrientation[1];
                    accMagDiff[2] = accMagOrientation[2] - accMagPrevOrientation[2];

                    /*  Add these diff values to accMagTrajectory
                     *  The getOrientation method returns value like so : -yaw, -pitch, roll (-z, -x, y)
                     *  To stay consistent throughout, I convert these here to the PRY (x,y,z)
                     *  I've followed throughout the code
                     **/
                    accMagTrajectory[0] -= accMagDiff[1]; // pitch
                    accMagTrajectory[1] += accMagDiff[2]; // roll
                    accMagTrajectory[2] -= accMagDiff[0]; // yaw
                }

                /* set cur values as prev */
                accMagPrevOrientation[0] = accMagOrientation[0];
                accMagPrevOrientation[1] = accMagOrientation[1];
                accMagPrevOrientation[2] = accMagOrientation[2];
            } else
                Log.e(TAG, "There was an error in calculating acc-mag orientation");
        }
    }

    private void calculateFusedOrientation() {
        fusedTrajectory[0] = (alpha * gyroTrajectoryCorrected[0]) + (oneMinusAlpha * accMagTrajectory[0]); // pitch
        fusedTrajectory[1] = (alpha * gyroTrajectoryCorrected[1]) + (oneMinusAlpha * accMagTrajectory[1]); // roll
        fusedTrajectory[2] = (alpha * gyroTrajectoryCorrected[2]) + (oneMinusAlpha * accMagTrajectory[2]); // yaw
        System.arraycopy(fusedTrajectory, 0, gyroTrajectoryCorrected, 0, fusedTrajectory.length);
    }

    public void reset() {
        gyroDiff = new float[3];
        accData = null;
        magData = null;

        accMagPrevOrientation = null;
        accMagOrientation = new float[3];
        accMagDiff = new float[3];
        accMagRotationMatrix = new float[16];

        accMagTrajectory = new float[3]; /* -yaw, -pitch, roll (-z, -x, y) */
        gyroTrajectoryCorrected = new float[3]; /* pitch, roll, yaw (x, y, z) */
        gyroTrajectoryRaw = new float[3]; /* pitch, roll, yaw (x, y, z) */
        fusedTrajectory = new float[3]; /* pitch, roll, yaw (x, y, z) */
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }
}