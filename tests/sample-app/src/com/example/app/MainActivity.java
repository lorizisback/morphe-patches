package com.example.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import com.some.app.ads.AdsLoader;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        AdsLoader loader = new AdsLoader();
        boolean ads = loader.showAds("banner", 1, this);
        String helper = loader.recordMessage("original");
        String result = "SAMPLE_RESULT ads=" + ads + " helper=" + helper;
        Log.i("SamplePatchFixture", result);

        TextView view = new TextView(this);
        view.setTextSize(22);
        view.setPadding(32, 96, 32, 32);
        view.setText("Sample patch fixture\n\nAds: " + (ads ? "visible" : "hidden")
                + "\n\nHelper: " + helper);
        setContentView(view);
    }
}
