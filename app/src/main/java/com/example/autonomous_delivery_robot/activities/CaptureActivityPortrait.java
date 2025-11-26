package com.example.autonomous_delivery_robot.activities;

import com.journeyapps.barcodescanner.CaptureActivity;
import android.os.Bundle;
import android.content.pm.ActivityInfo;

public class CaptureActivityPortrait extends CaptureActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
}