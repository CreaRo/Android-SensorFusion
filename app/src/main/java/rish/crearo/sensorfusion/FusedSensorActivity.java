package rish.crearo.sensorfusion;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import rish.crearo.sensorfusionlib.SensorFusion;
import rish.crearo.sensorfusionlib.listeners.VerboseFusionListener;

import static java.lang.Math.toDegrees;

public class FusedSensorActivity extends AppCompatActivity implements VerboseFusionListener {

    private static final String TAG = FusedSensorActivity.class.getSimpleName();
    private SensorFusion mSensorFusion;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorFusion = new SensorFusion(this, this);
        textView = (TextView) findViewById(R.id.main_textview);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSensorFusion.stop();
                mSensorFusion.reset();
                mSensorFusion.start();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorFusion.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorFusion.stop();
    }

    @Override
    public void onFusedOrientation(float[] fusedOrientation, long timestamp) {
        Log.d(TAG, getFormatStringForOrientation(fusedOrientation));
        textView.setText("Fused Orientation is : "
                + getFormatStringForOrientation(fusedOrientation));
    }

    @Override
    public void onGyroOrientation(float[] gyroOrientation, long timestamp) {
        Log.d(TAG, getFormatStringForOrientation(gyroOrientation));
    }

    @Override
    public void onAccMagOrientation(float[] accMagOrientation, long timestamp) {
        Log.d(TAG, getFormatStringForOrientation(accMagOrientation));
    }

    /**
     * Helper function
     *
     * @param orientation
     * @return formatted string; PRY (pitch roll yaw) ie, rotation about [x, y, z].
     */
    private String getFormatStringForOrientation(float orientation[]) {
        String print = String.format("pitch = %d\nroll = %d\nyaw  %d",
                toDegrees(orientation[0]),
                toDegrees(orientation[1]),
                toDegrees(orientation[2]));
        return print;
    }
}