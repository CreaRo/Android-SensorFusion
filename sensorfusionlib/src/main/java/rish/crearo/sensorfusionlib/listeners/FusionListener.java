package rish.crearo.sensorfusionlib.listeners;

/**
 * Created by rish on 12/7/17.
 */

public interface FusionListener {
    /**
     * @param fusedOrientation PRY (x, y, z)
     */
    void onFusedOrientation(float fusedOrientation[], long timestamp);
}