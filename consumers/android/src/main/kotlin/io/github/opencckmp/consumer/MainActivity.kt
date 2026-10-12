package io.github.opencckmp.consumer

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = Fixture.run(this)
        check(output == "漢字轉換")
        Log.i("OpenCCFixture", "PASS $output")
        setContentView(TextView(this).apply { text = output })
    }
}
