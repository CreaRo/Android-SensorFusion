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

    private SensorManager mSensorManager;
    private Sensor mSensorGyro, mSensorAcc, mSensorMag;

    /* Nanoseconds to seconds */
    private static final float NS2S = 1.0f / 1000000000.0f;

    private long mPrevGyroTimestamp = 0;

    /**
     * This is the absolute difference in sensor values from the previously recorded reading.
     * For gyroscope, it is simply the integration of angular velocity (angVel * dT)
     * For acc/mag, it is the difference in orientation angles (YPR) obtained using
     * SensorManager.getOrientation(rotationMatrix) between current and prev orientations
     **/
    private float mGyroDiff[] = new float[3];
    private float mAccMagDiff[] = new float[3];

    /**
     * Using these to clone values received from SensorEventListener callback
     */
    private float mAccData[], mMagData[];

    /**
     * Stores YPR(in that order) of the fused magnetometer and accelerometer obtained by calling
     * SensorManager.getRotationMatrix(acc, mag), and then passing the rotation matrix received to
     * SensorManager.getOrientation(rot).
     */
    private float mAccMagPrevOrientation[];
    private float mAccMagOrientation[] = new float[3];

    /* temp variable storing rotation matrix */
    private float mAccMagRotationMatrix[] = new float[16];

    /* pitch, roll, yaw (x, y, z) */
    private float mAccMagTrajectory[] = new float[3];
    private float mGyroTrajectoryCorrected[] = new float[3];
    private float mGyroTrajectoryRaw[] = new float[3];
    private float mFusedTrajectory[] = new float[3];

    private VerboseFusionListener mVerboseFusionListener;
    private FusionListener mFusionListener;

    public SensorFusion(Context context, VerboseFusionListener verboseFusionListener) {
        initSensors(context);
        mVerboseFusionListener = verboseFusionListener;
    }

    public SensorFusion(Context context, FusionListener fusionListener) {
        initSensors(context);
        mFusionListener = fusionListener;
    }

    private void initSensors(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensorGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorMag = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void start() {
        mSensorManager.registerListener(this, mSensorGyro, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mSensorAcc, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mSensorMag, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void stop() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == mSensorGyro.getType()) {
            calculateRawGyroOrientation(event.values, event.timestamp);
            if (mVerboseFusionListener != null)
                mVerboseFusionListener.onGyroOrientation(mGyroTrajectoryRaw, event.timestamp);
            calculateFusedOrientation();
            if (mVerboseFusionListener != null)
                mVerboseFusionListener.onFusedOrientation(mFusedTrajectory, event.timestamp);
            if (mFusionListener != null)
                mFusionListener.onFusedOrientation(mFusedTrajectory, event.timestamp);
        } else if (event.sensor.getType() == mSensorMag.getType()) {
            if (mMagData == null) mMagData = new float[3];
            System.arraycopy(event.values, 0, mMagData, 0, event.values.length);
        } else if (event.sensor.getType() == mSensorAcc.getType()) {
            if (mAccData == null) mAccData = new float[3];
            System.arraycopy(event.values, 0, mAccData, 0, event.values.length);
            calculateAccMagOrientation();
            if (mVerboseFusionListener != null)
                mVerboseFusionListener.onAccMagOrientation(mAccMagTrajectory, event.timestamp);
        }
    }

    private void calculateRawGyroOrientation(float angularVelocity[], long timestamp) {
        if (mPrevGyroTimestamp != 0) {
            float dt = (timestamp - mPrevGyroTimestamp) * NS2S;
            mGyroDiff[0] = dt * angularVelocity[0];
            mGyroDiff[1] = dt * angularVelocity[1];
            mGyroDiff[2] = dt * angularVelocity[2];
        }
        mPrevGyroTimestamp = timestamp;

        /* Add these diff values to raw and corrected trajectory, just the same */
        mGyroTrajectoryRaw[0] += mGyroDiff[0];
        mGyroTrajectoryRaw[1] += mGyroDiff[1];
        mGyroTrajectoryRaw[2] += mGyroDiff[2];

        mGyroTrajectoryCorrected[0] += mGyroDiff[0];
        mGyroTrajectoryCorrected[1] += mGyroDiff[1];
        mGyroTrajectoryCorrected[2] += mGyroDiff[2];
    }

    private void calculateAccMagOrientation() {
        if (mMagData != null && mAccData != null) {
            if (SensorManager.getRotationMatrix(mAccMagRotationMatrix, null, mAccData, mMagData)) {
                SensorManager.getOrientation(mAccMagRotationMatrix, mAccMagOrientation);
                if (mAccMagPrevOrientation == null) {
                    mAccMagPrevOrientation = new float[3];
                    mAccMagDiff = new float[3];
                } else {
                    mAccMagDiff[0] = mAccMagOrientation[0] - mAccMagPrevOrientation[0];
                    mAccMagDiff[1] = mAccMagOrientation[1] - mAccMagPrevOrientation[1];
                    mAccMagDiff[2] = mAccMagOrientation[2] - mAccMagPrevOrientation[2];

                    /**
                     *  Add these diff values to mAccMagTrajectory
                     *  The getOrientation method returns value like so : -yaw, -pitch, roll (-z, -x, y)
                     *  To stay consistent throughout, I convert these here to the PRY (x,y,z)
                     *  I've followed throughout the code
                     **/
                    mAccMagTrajectory[0] -= mAccMagDiff[1]; // pitch
                    mAccMagTrajectory[1] += mAccMagDiff[2]; // roll
                    mAccMagTrajectory[2] -= mAccMagDiff[0]; // yaw
                }

                /* set cur values as prev */
                mAccMagPrevOrientation[0] = mAccMagOrientation[0];
                mAccMagPrevOrientation[1] = mAccMagOrientation[1];
                mAccMagPrevOrientation[2] = mAccMagOrientation[2];
            } else
                Log.e(TAG, "There was an error in calculating acc-mag orientation");
        }
    }

    private float alpha = 0.995f;
    private float oneMinusAlpha = 1.0f - alpha;

    private void calculateFusedOrientation() {
        mFusedTrajectory[0] = (alpha * mGyroTrajectoryCorrected[0]) + (oneMinusAlpha * mAccMagTrajectory[0]); // pitch
        mFusedTrajectory[1] = (alpha * mGyroTrajectoryCorrected[1]) + (oneMinusAlpha * mAccMagTrajectory[1]); // roll
        mFusedTrajectory[2] = (alpha * mGyroTrajectoryCorrected[2]) + (oneMinusAlpha * mAccMagTrajectory[2]); // yaw
        System.arraycopy(mFusedTrajectory, 0, mGyroTrajectoryCorrected, 0, mFusedTrajectory.length);
    }

    public void reset() {
        mGyroDiff = new float[3];
        mAccData = null;
        mMagData = null;

        mAccMagPrevOrientation = null;
        mAccMagOrientation = new float[3];
        mAccMagDiff = new float[3];
        mAccMagRotationMatrix = new float[16];

        mAccMagTrajectory = new float[3]; /* -yaw, -pitch, roll (-z, -x, y) */
        mGyroTrajectoryCorrected = new float[3]; /* pitch, roll, yaw (x, y, z) */
        mGyroTrajectoryRaw = new float[3]; /* pitch, roll, yaw (x, y, z) */
        mFusedTrajectory = new float[3]; /* pitch, roll, yaw (x, y, z) */
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // do nothing
    }
}