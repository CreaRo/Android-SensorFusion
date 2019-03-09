package rish.crearo.sensorfusion;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import rish.crearo.sensorfusion.views.AttitudeIndicator;
import rish.crearo.sensorfusionlib.OrientationFromRotation;

public class RotationActivity extends AppCompatActivity implements OrientationFromRotation.Listener {

    private OrientationFromRotation orientationFromRotation;
    private AttitudeIndicator attitudeIndicator;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rotation);

        orientationFromRotation = new OrientationFromRotation(this);
        attitudeIndicator = (AttitudeIndicator) findViewById(R.id.attitude);
        textView = findViewById(R.id.tv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        orientationFromRotation.startListening(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        orientationFromRotation.stopListening();
    }

    @Override
    public void onOrientationChanged(float azimuth, float pitch, float roll) {
        attitudeIndicator.setAttitude(pitch, roll);
        textView.setText(String.format("Heading: %d", ((int) azimuth)));
    }
}
