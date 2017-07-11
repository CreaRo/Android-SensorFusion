package rish.crearo.sensorfusionlib.listeners;

/**
 * Created by rish on 12/7/17.
 */

public interface VerboseFusionListener extends FusionListener {
    /**
     * @param gyroOrientation PRY (x, y, z)
     */
    void onGyroOrientation(float gyroOrientation[], long timestamp);

    /**
     * @param accMagOrientation PRY (x, y, z)
     */
    void onAccMagOrientation(float accMagOrientation[], long timestamp);
}