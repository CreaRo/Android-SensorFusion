package rish.crearo.sensorfusionlib;

import android.app.Activity;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

import rish.crearo.sensorfusionlib.listeners.RotationListener;

public class OrientationFromRotation implements RotationListener {

    private static final String TAG = OrientationFromRotation.class.getSimpleName();
    private final WindowManager windowManager;
    private final RotationSensor rotationSensor;
    private float[] adjustedRotationMatrix = new float[9];

    private Listener listener;

    public OrientationFromRotation(Activity activity) {
        windowManager = activity.getWindow().getWindowManager();
        rotationSensor = new RotationSensor(activity.getApplicationContext(), this);
    }

    public void startListening(Listener listener) {
        if (this.listener == listener) {
            return;
        }
        this.listener = listener;
        rotationSensor.start();
    }

    public void stopListening() {
        rotationSensor.stop();
        listener = null;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void updateOrientation(float[] rotationMatrix) {
        final int worldAxisForDeviceAxisX;
        final int worldAxisForDeviceAxisY;

        // Remap the axes as if the device screen was the instrument panel,
        // and adjust the rotation matrix for the device orientation.
        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
            default:
                worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                break;
            case Surface.ROTATION_90:
                worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                break;
            case Surface.ROTATION_270:
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                break;
        }

        SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                worldAxisForDeviceAxisY, adjustedRotationMatrix);

        // Transform rotation matrix into azimuth/pitch/roll
        float[] orientation = new float[3];
        SensorManager.getOrientation(adjustedRotationMatrix, orientation);

        // Convert radians to degrees
        float pitch = orientation[1] * -57;
        float roll = orientation[2] * -57;
        float azimuth = orientation[0] * -57;

        listener.onOrientationChanged(azimuth, pitch, roll);
    }

    @Override
    public void onRotationMatrix(float[] rotationMatrix, float timestamp) {
        if (listener == null) {
            return;
        }
        updateOrientation(rotationMatrix);
    }

    public interface Listener {
        void onOrientationChanged(float azimuth, float pitch, float roll);
    }
}
