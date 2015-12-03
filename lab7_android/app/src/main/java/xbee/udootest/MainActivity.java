package xbee.udootest;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import me.palazzetti.adktoolkit.AdkManager;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.FastVector;
import weka.core.SerializationHelper;
import wlsvm.WLSVM;

public class MainActivity extends Activity{

//	private static final String TAG = "UDOO_AndroidADKFULL";	 

    private AdkManager mAdkManager;

    private ToggleButton buttonLED;
    private TextView distance;
    private TextView pulse;
    private TextView position;

    private AdkReadTask mAdkReadTask;

    private BufferedReader inputReader;
    private WLSVM svmCls;
    private FastVector fvClassVal;
    private FastVector fvWekaAttributes;
    private File root;
    private Instances data;
    private Instances testingSet;

    private static final String svmModel = "/svmModel";
    private static final String trainingFile = "pulse_train.arff";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAdkManager = new AdkManager((UsbManager) getSystemService(Context.USB_SERVICE));

//		register a BroadcastReceiver to catch UsbManager.ACTION_USB_ACCESSORY_DETACHED action
   //     registerReceiver(mAdkManager.getUsbReceiver(), mAdkManager.getDetachedFilter());

        buttonLED = (ToggleButton) findViewById(R.id.toggleButtonLED);
        distance  = (TextView) findViewById(R.id.textView_distance);
        pulse  = (TextView) findViewById(R.id.textView_pulse);
        position  = (TextView) findViewById(R.id.textView_position);

        // build model from file
        buildModel();

        // build new instances
        Attribute attribute1 = new Attribute("pulse");
        Attribute attribute2 = new Attribute("oxygen");

        fvClassVal = new FastVector(3);
        fvClassVal.addElement("Init");
        fvClassVal.addElement("Rest");
        fvClassVal.addElement("Stress");
        Attribute classAttribute = new Attribute("class", fvClassVal);

        fvWekaAttributes = new FastVector(3);
        fvWekaAttributes.addElement(attribute1);
        fvWekaAttributes.addElement(attribute2);
        fvWekaAttributes.addElement(classAttribute);

        testingSet = new Instances("TestingInstance", fvWekaAttributes, 1);
        testingSet.setClassIndex(testingSet.numAttributes() - 1);
    }

    @Override
    public void onResume() { // activity is in foreground (running state)
        super.onResume();
        mAdkManager.open();

        mAdkReadTask = new AdkReadTask();
        mAdkReadTask.execute();
    }

    @Override
    public void onPause() {  // activity is not in the foreground but still alive
        super.onPause();
        mAdkManager.close();

        mAdkReadTask.pause();
        mAdkReadTask = null;
    }

    @Override
    public void onDestroy() { // activity is about to be destroyed
        super.onDestroy();  // call super class implementation of onDestroy
   //     unregisterReceiver(mAdkManager.getUsbReceiver());
    }

    private void buildModel() {

        root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); 
        try {
            // Open file for reading
            svmCls = (WLSVM) weka.core.SerializationHelper.read(root + svmModel);
        }
        catch (IOException ex) {            // No existing model file found
            Log.e("exception", Log.getStackTraceString(ex));
            // Open training data from file
            root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); 
            File f = new File(root, "iris_train.arff"); 
            inputReader = readFile(f);  

            try {
                // Get instances from BufferedReader
                data = new Instances(inputReader);
                // Make the last attribute be the class
                data.setClassIndex(data.numAttributes() - 1);
                svmCls = new WLSVM();
                svmCls.buildClassifier(data);
                weka.core.SerializationHelper.write(root + svmModel, svmCls);
                inputReader.close();
            }
            catch (IOException e) {
                Log.e("exception", Log.getStackTraceString(e));
            }
            catch (Exception e) {
                Log.e("exception", Log.getStackTraceString(e));
            }
        }
        catch (Exception ex) {
            Log.e("exception", Log.getStackTraceString(ex));
        }
    }

    private BufferedReader readFile(File f) {
        try {
            return new BufferedReader(new FileReader(f));
        }
        catch (FileNotFoundException ex) {
            Log.e("Exception", Log.getStackTraceString(ex));
        }
        return null;
    }

    // ToggleButton method - send message to SAM3X
    public void blinkLED(View v){
        if (buttonLED.isChecked()) {
            // writeSerial() allows you to write a single char or a String object.
          mAdkManager.writeSerial("1");
        } else {
            mAdkManager.writeSerial("0");
        }
    }

    /*
     * We put the readSerial() method in an AsyncTask to run the
     * continuous read task out of the UI main thread
     */
    private class AdkReadTask extends AsyncTask<Void, String, Void> {

        private boolean running = true;

        public void pause(){
            running = false;
        }

        protected Void doInBackground(Void... params) {
//	    	Log.i("ADK demo bi", "start adkreadtask");
            while(running) {
                String toPublish = mAdkManager.readSerial();
                if (toPublish != null)
                    publishProgress(toPublish);
            }
            return null;
        }

        protected void onProgressUpdate(String... progress) {

            float pres= (int)progress[0].charAt(0);
            float pul= (int)progress[0].charAt(1);
            float pos= (int)progress[0].charAt(2);
            int max = 255;
            if (pres>max) pres=max;
            if (pul>max) pul=max;
            if (pos>max) pos=max;

//            DecimalFormat df = new DecimalFormat("#.#");
            distance.setText(pres + " bpm");
            pulse.setText(pul + "");
            position.setText(pos + "");

            startClassification(pres, pul);
        }
    }

    public void startClassification(float pulse, float oxygen) {
        double pulseValue, oxygenValue;
        pulseValue = 0;
        oxygenValue = 0;

        pulseValue = pulse;
        oxygenValue = oxygen;

        Instance iExample = new Instance(testingSet.numAttributes());
        iExample.setValue((Attribute)fvWekaAttributes.elementAt(0), pulseValue);
        iExample.setValue((Attribute)fvWekaAttributes.elementAt(1), oxygenValue);
        iExample.setValue((Attribute)fvWekaAttributes.elementAt(4), "Rest"); // dummy

        testingSet.add(iExample);

        try {
            int prediction = (int)svmCls.classifyInstance(testingSet.lastInstance());
            String output = fvClassVal.elementAt(prediction).toString();
            Toast.makeText(getApplicationContext(), output, Toast.LENGTH_SHORT).show();
        }
        catch (Exception ex) {
            Log.e("exception", Log.getStackTraceString(ex));
        }
    }

    public void startTraining(View view) {
        try {
            // Open file for reading
            root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); 
            svmCls = (WLSVM) weka.core.SerializationHelper.read(root + svmModel);
            Toast.makeText(getApplicationContext(), "Trained", Toast.LENGTH_SHORT).show();
        }
        catch (IOException ex) {            // No existing model file found
            Log.e("exception", Log.getStackTraceString(ex));
        }
        catch (Exception ex) {
            Log.e("exception", Log.getStackTraceString(ex));
        }
    }
}
