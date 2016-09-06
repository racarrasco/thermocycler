package com.example.rigo_carrasco.PhotonicPCR;


import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;

import android.os.Handler;
import android.os.IBinder;
import android.os.Message;



import android.view.View;

import android.view.WindowManager;
import android.widget.Button;


import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.github.mikephil.charting.charts.LineChart;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;


import java.io.File;
import java.io.IOException;

import java.lang.ref.WeakReference;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity  {
    /*
     * Notifications from UsbService will be received here.
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { //Listens to the status of the usb
            switch (intent.getAction()) {
                case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                    Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                    runButton.setEnabled(true);
                    break;
                case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                    Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                    runButton.setEnabled(false);
                    break;
                case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                    Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                    runButton.setEnabled(false);
                    break;
                case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                    Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                    runButton.setEnabled(false);
                    break;
                case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                    Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                    runButton.setEnabled(false);
                    break;
            }
        }
    };
    private UsbService usbService; //usb service does the reading and writing from serial
    private TextView textView; //notifications from the app
    String[] parametersActivity; //parameters for thermocycling
    Button clearButton, runButton,controlButton, sendDataButton;
    TextView cycles,temperature,current_cycle,timeElapsed;
    EditText FILENAME;
    ArrayList<Float> overallTemp = new ArrayList<>(); //For plotting the data in the end
    ArrayList<Float> overallTime = new ArrayList<>();
    File internalFile = null; // to send the temperature data as an email
    String Body = null;












    LineChart chart;




    private MyHandler mHandler; //handler for the usb service
    //Bind to Service
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) { //This is when the current activity is connected to the UsbService.class
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        mHandler = new MyHandler(this); //This allows the activity to receive messages from UsbService
        cycles = (TextView) findViewById(R.id.editTextNumberOfCycles); //Number of cycles
        temperature = (TextView) findViewById(R.id.textViewCurrentTemperature);
        current_cycle = (TextView) findViewById(R.id.textViewCurrentCycle);
        controlButton = (Button) findViewById(R.id.buttonToControl);
        sendDataButton = (Button) findViewById(R.id.buttonSendData);
        FILENAME = (EditText) findViewById(R.id.filenameEditText); //Filename
        timeElapsed = (TextView) findViewById(R.id.timeElapsedTextView);






        //inital thermalcycling parameters
        parametersActivity = new String[28];
        for(int i = 0; i<11;i++){
            parametersActivity[i]="";
        }



        parametersActivity[11] = "30";
        parametersActivity[5] = "65";
        parametersActivity[6] = "0";
        parametersActivity[7]  = "45";
        parametersActivity [8] = "0";
        String kp = "7";
        String ki = "2";
        String kd = "1";
        for (int h=0;h<5; h++){
            //setting the initial PID parameters
            parametersActivity[12+3*h] = kp;
            parametersActivity[13+3*h] = ki;
            parametersActivity[14+3*h] = kd;
        }
        parametersActivity[27] = "Time(s):";








        //Chart
        chart = (LineChart) findViewById(R.id.lineChart);
        chart.setDescription("Time (s)");
        chart.setDescriptionTextSize(13f);
        chart.setNoDataTextDescription("No data yet.");
        chart.setBackgroundColor(Color.LTGRAY);

        //allowing touch interactions
        chart.setTouchEnabled(true);

        chart.setDragEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);

        chart.setHighlightPerTapEnabled(false);
        chart.setHighlightPerDragEnabled(false);




        //data




        //chart
       chart.setData(new LineData());




        //Xaxis
        XAxis xl = chart.getXAxis();
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);






        //Yaxis
        YAxis yl = chart.getAxisLeft();
        yl.setAxisMaxValue(100f);
        yl.setDrawGridLines(true);

        YAxis yl2 = chart.getAxisRight();
        yl2.setEnabled(false);




        textView = (TextView) findViewById(R.id.textView);
        clearButton = (Button) findViewById(R.id.buttonClear);
        runButton = (Button) findViewById(R.id.buttonRun);

        runButton.setEnabled(false);
       sendDataButton.setEnabled(false);
    }
    private void removeDataSet() { //clears the chart linedata

        chart.setData(new LineData());
        chart.setTouchEnabled(true);
        chart.notifyDataSetChanged();
        chart.fitScreen();
    }




    public void pushcmd(String command) { //method that writes commands to arduino
        usbService.write(command.getBytes());
    }

    public String encnum(Object value) { //encode command for serial communication
        int intval = (Integer) value;
        String strval = Integer.toString(intval);
        String cmdstr;
        if (intval < 10) {
            cmdstr = "0" + "0" + strval;
        } else if (intval < 100) {
            cmdstr = "0" + strval;
        } else
            cmdstr = strval;
        return cmdstr;
    }



    public void onClickClear(View view) { //Clear the chart and any textviews
        removeDataSet();
        textView.setText(" ");
        current_cycle.setText(" ");
        temperature.setText(" ");
        timeElapsed.setText(" ");
        overallTime.clear();
        overallTemp.clear();
        if(internalFile!=null)
            internalFile.delete();
    }


    public void onClickRun(View view) {
        List<Map<String, Object>> cmd = encodecmd(parametersActivity);//encode commands for the arduino
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);  //This is the time stamp for when the run button was pushed

        Body = "\nPCR started on: " + DateFormat.getDateTimeInstance().format(new Date()); //The body for the email
        tvSet(current_cycle,Integer.toString(1)); // Sets shows that the current cycle is 1
        controlButton.setEnabled(false); //prevents user from going to this window/ activity screen
        runButton.setEnabled(false); //prevents user from pressing the run button again
        removeDataSet(); //clears the displayed data
        temperature.setText(" ");
        clearButton.setEnabled(false);
        ArrayList<Float> graph = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            if (!parametersActivity[i].isEmpty()) {
                graph.add(Float.parseFloat(parametersActivity[i]));
            }
        }
        showCyclingParameters(graph); // allows user to see the cycling parameters on the graph
        Run(cmd); // sends all of the cycling parameters to the arduino to store
        sendDataButton.setEnabled(false); //prevents user to send data once thermocycling begins
        if(internalFile!=null) //deletes any files
            internalFile.delete();
        cycles.setText(parametersActivity[11]); //number of specified cycles
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //keeps screen on during thermocycling
        runplot(); //starts the interfacing loop

    }
    public void onClickStop(View view) { // stop thermocycling
        pushcmd("P\n"); //stop
        pushcmd("R\n");
        tvAppend(textView,"\nStopping");
        //allow user to access functions/activity screens again
        clearButton.setEnabled(true);
        controlButton.setEnabled(true);
        runButton.setEnabled(true);
    }


    public void runplot() { //initial command that starts the loop in the handler
        {pushcmd("S\n");}
    }






    @Override
    public void onResume() {
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection, null);// Start UsbService(if it was not started before) and Bind it
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    public void tvAppend(TextView tv, CharSequence text) { //This is to simply append to the textview
        final TextView ftv = tv;
        final CharSequence ftext = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ftv.append(ftext);
            }
        });
    }

    public void tvSet(TextView tv,CharSequence text) {//set text to a textview
        final TextView ftv = tv;
        final CharSequence ftext = text;
        runOnUiThread((new Runnable() {
            @Override
            public void run() {
                ftv.setText(ftext);
            }
        }));
    }


    private void plotData(ArrayList<Float> time, ArrayList<Float> temp) { // plots the time and temperature data
        removeDataSet(); //removes any data from the chart
        LineData data = chart.getData();
        if(data !=null) {
            ILineDataSet set = data.getDataSetByIndex(0);
            if (set == null) {
                set = createSet(true); //reset x axis to skip labels
                data.addDataSet(set); //plot all of the data
            }
            for(int i = 0; i<time.size();i++) {
                String xentry = Float.toString(time.get(i));
                data.addXValue("" + xentry);
                data.addEntry(new Entry(temp.get(i), set.getEntryCount()), 0);
            }
            float xrange = 1200;
            chart.notifyDataSetChanged();
            chart.setVisibleXRangeMaximum(xrange);
            chart.highlightValue(-1,0);
            chart.moveViewToX(data.getXValCount());
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sendDataButton.setEnabled(true);
    }


    private void showCyclingParameters(ArrayList<Float> temp ) { //plots the parameters on the graph
        int pc=0; //indicates if there is a precool stage or not. 0 means there is no precool stage and 1 means there is a precool stage
        LineData data = chart.getData();
            if (data != null) {
                ILineDataSet set = data.getDataSetByIndex(0); //gets the data set
                if (set == null) {
                    set = createSet(false); //set no skipping x axis label
                    data.addDataSet(set);

                    data.addXValue("");
                    data.addEntry(new Entry(0f,set.getEntryCount()),0);
                }
                if (temp.size() % 2 != 0) { //we dont have a precooling stage because parameters come in pairs except for the precool stage
                    pc++;
                    if(parametersActivity[27].equals("Time(s):")){
                        data.addEntry(new Entry(0f, data.getXValCount()),0);
                        data.addXValue(temp.get(0)+"sec");
                    }
                    else{
                        data.addEntry( new Entry(temp.get(0),data.getXValCount()),0);
                        data.addXValue("0.0sec");
                    }
                }
                for(int i=pc; i<temp.size();i+=2) { //This is to allow highling in certain regions of the graph
                    data.addDataSet(createSet(false));
                    int lastset= data.getDataSets().size()-1;
                    Entry old_value; //the overlapping value
                    if(i>1) //it will get the last entry and overlap that entry
                        old_value = new Entry(temp.get(i - 2), data.getXValCount() - 1);
                    else if(pc==1&&!parametersActivity[27].equals("Time(s):")) // if precooling is to a certain temperature
                        old_value = new Entry (temp.get(0),data.getXValCount()-1);
                    else
                        old_value = new Entry(0f,data.getXValCount()-1); // if there is no precool stage and we are looking at the first pair of parameters
                    Entry new_value = new Entry(temp.get(i),data.getXValCount()); //the new value
                    Entry extra_value = new Entry(temp.get(i),data.getXValCount()+1); //the value that will show if we need to maintain a certain temperature for a certain amount of time

                    data.addEntry(old_value,lastset);
                    data.addXValue(temp.get(i+1)+"sec");
                    data.addEntry(new_value,lastset);
                    if(temp.get(i+1)!=0){
                        data.addDataSet(createSet(false));
                        data.addEntry(new_value,lastset+1);
                        data.addXValue(" ");
                        data.addEntry(extra_value,lastset+1);
                    }
                }

                chart.notifyDataSetChanged();
                chart.invalidate();
            }
    }


    private LineDataSet createSet(boolean skiplabels){ //creates a new linedataset
        LineDataSet set = new LineDataSet(null,"Temperatures");

        if(!skiplabels){
        chart.getXAxis().setLabelsToSkip(0);
        chart.getLegend().setEnabled(false);}
        else{
        chart.getXAxis().resetLabelsToSkip();
        chart.getLegend().setEnabled(true);}

        set.setCubicIntensity(0.2f);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.BLACK);
        set.setCircleColor(Color.BLACK);
        set.setLineWidth(2f);
        set.setFillAlpha(65);
        set.setFillColor(Color.BLACK);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(8f);
        set.setDrawHorizontalHighlightIndicator(false);
        set.setHighlightLineWidth(10f);
        set.setHighLightColor(ColorTemplate.getHoloBlue());
        set.setFillColor(Color.RED);
        chart.zoom(.3f,.3f,0f,0f);


        return set;
    }

    public void onClickSendData(View view) { //to send data as an email

        String filename = FILENAME.getText().toString()+".csv";
        String data = "Time (s)" + "," + "Temperature (C)"+"\n";
        for (int i = 0;i<overallTime.size();i++){
            data+= overallTime.get(i).toString()+"," +overallTemp.get(i).toString()+"\n";
        }

        try{
            internalFile = InternalFileUtils.createInternalFile(MainActivity.this,filename,data);
            startActivity(InternalFileUtils.getSendEmailIntent(MainActivity.this,"","Thermocycler data",Body,filename));

        }catch (IOException e) {
            e.printStackTrace();
        }

        catch (ActivityNotFoundException e) {
            Toast.makeText(MainActivity.this,"Gmail is not available on this device.",
                    Toast.LENGTH_SHORT).show();
        }


        overallTemp.clear();
        overallTime.clear();
    }


    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;
        float precooltime;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }


        //Array slicing method similar to python's array [begin:end:spacing]
        public  float[] sliceArray(float[] arr, int begin,int end, int spacing) {
            int curr = begin;
            float[] newArr = new float [((end - begin - 1) / spacing) + 1];
            for (int i = 0; i < newArr.length; i++) {
                newArr[i] = arr[curr];
                curr += spacing;
            }
            return newArr;
        }

       public void update_data(float[] times,float[] temps) { //filters through data received by the arduino
           ArrayList<Float> updatedtemp = new ArrayList<>();
          // ArrayList<Float> updatedtime = new ArrayList<>(); in case real time graphing is desired
           float dtime_recent; //Checks if data from the arduino is new
           if (mActivity.get().overallTime.size() == 0) {
               dtime_recent = 0;
           } else {
               dtime_recent = mActivity.get().overallTime.get(mActivity.get().overallTime.size() - 1);
           }
           for (int i = 0; i < times.length; i++) {
               if (times[i] > dtime_recent) { //makes sure that the data received is new data and not repetitive
                   mActivity.get().overallTime.add(times[i]);
                   mActivity.get().overallTemp.add(temps[i]);
                   updatedtemp.add(temps[i]);
                   if(updatedtemp.size()>1){
                       if(updatedtemp.get(updatedtemp.size()-2)!=temps[i]){
                           mActivity.get().tvSet(mActivity.get().temperature, Float.toString(temps[i]));

                           mActivity.get().timeElapsed.setText(String.format(Locale.US,"%.2f",times[i]-precooltime));
                       }
                   }
                   else{
                       mActivity.get().tvSet(mActivity.get().temperature, Float.toString(updatedtemp.get(0)));
                       mActivity.get().timeElapsed.setText(String.format(Locale.US,"%.2f",times[i]-precooltime));
                   }

               }
           }
       }

        public float [] logtimes(float[] log) { //slices the data from the arduino to retrieve time in seconds and milliseconds
            float[] log_time1 = sliceArray(log, 0,log.length, 3);
            float[] log_time2 = sliceArray(log, 1,log.length, 3);
            float[] log_time = new float[log_time1.length];
            for (int i = 0; i < log_time1.length; i++) {
                log_time[i] = log_time1[i] + log_time2[i]/1000;
            }
            return log_time;
        }



        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
                   String [] data = (String[]) msg.obj;
                    /*the if statement checks for what kind of data was sent from the arduino
                     either a pointer  that indicates that it is still thermocycling or if it is the temperature/time
                     array to be graphed
                     */
                    if (data.length<100) { //cycling status
                        if (!data[0].equals("0")) { //if pointer isnt 0 prompt arduino to send data
                            mActivity.get().current_cycle.setText(data[1]);
                            mActivity.get().highlight(data[2],data[3]); //highlighting method
                            precooltime = Float.parseFloat(data[4]);
                            mActivity.get().pushcmd("L\n");
                        } else { //if pointer is zero, plot data
                            mActivity.get().plotData(mActivity.get().overallTime,mActivity.get().overallTemp);
                            precooltime = Float.parseFloat(data[4]);
                            mActivity.get().current_cycle.setText("done/end");
                            mActivity.get().clearButton.setEnabled(true);
                            mActivity.get().controlButton.setEnabled(true);
                            mActivity.get().runButton.setEnabled(true);
                        }
                    }
                    else{
                        //handles the data from the arduino
                        float [] floats = new float[data.length-1]; //This is to make the data into floats
                       for (int i = 0; i<floats.length;i++) { //the -1 is to remove the "\n" in arduino's output
                           floats[i] = Float.parseFloat(data[i]);
                       }
                        float [] times =  logtimes(floats); //calls function to get time data
                        float [] temps = sliceArray(floats,2,floats.length,3); //similar to python's array[2::3]
                        update_data(times,temps); //checks if data is actually updated data
                        mActivity.get().pushcmd("S\n");
                    }
                    break;
                case UsbService.CTS_CHANGE:
                    Toast.makeText(mActivity.get(), "CTS_CHANGE", Toast.LENGTH_LONG).show();
                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(mActivity.get(), "DSR_CHANGE", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    public void onClickToParameters(View view) { //Takes user to another screen to set the parameters for thermal cycling
        Intent parametersScreenIntent = new Intent(this, ParametersScreen.class);
        final int result = 1;
        String [] theValues = parametersActivity;
        parametersScreenIntent.putExtra("values",theValues);
        /*First visit to the parameters screens
        are default parameters, once the parameters are set, they will be saved and the
        user will be able to refer to the screen*/
        startActivityForResult(parametersScreenIntent,result);
    }
    public void onClickToPID(View view) {
        Intent pIDScreenIntent = new Intent(this,PID.class);
        final int result = 1;
        String [] theValues = parametersActivity;
        pIDScreenIntent.putExtra("values",theValues);
        startActivityForResult(pIDScreenIntent,result);
    }
    public void onClickToControl(View view) {
        Intent controlScreenIntent = new Intent(this,Controls.class);
        final int result = 1;
        String [] theValues = parametersActivity;
        controlScreenIntent.putExtra("values",theValues);
        startActivityForResult(controlScreenIntent,result);
    }
    public void onClickBackToMain(View view) {

    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) { //sets the paramaters once the users goes back to this screen
        super.onActivityResult(requestCode, resultCode, data);
        parametersActivity = data.getStringArrayExtra("values"); //Using the same fields allow
        //for a "Save" feature in parameters
        runButton.setEnabled(true);
        ArrayList<Float> graph = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            if (!parametersActivity[i].isEmpty()) {
                graph.add(Float.parseFloat(parametersActivity[i]));
            }

        }
        removeDataSet();
        showCyclingParameters(graph);
    }

    public void highlight(String type,String target) { // highlight current stage in thermocycling
        LineData a = chart.getData();
        List<ILineDataSet> sets =a.getDataSets(); //data set
        float ftarget = Float.parseFloat(target); //the target temperature
        if(type.equals("O") | type.equals("K")) {
            for(int i= 0;i<sets.size();i++) {
                for(int j =0;j<a.getXValCount();j++) {
                    if (ftarget == sets.get(i).getYValForXIndex(j) && ftarget == sets.get(i).getYValForXIndex(j + 1)) {//the target data
                        if (type.equals("O")) {
                            sets.get(i - 1).setDrawFilled(false);
                        }
                        sets.get(i).setDrawFilled(true);
                       // tvAppend(textView,"kooling");
                        break;
                    }
                }
            }
        }

        else{
            if(type.equals("H"))
                sets.get(sets.size()-1).setDrawFilled(false);
            for (int i =0;i<sets.size();i++) {
                int j= -1;
                 do{
                     j++;
                     if(j==a.getXValCount())
                         break;
                    if (ftarget == sets.get(i).getYValForXIndex(j+1) && ftarget != sets.get(i).getYValForXIndex(j)) {
                        if(i!=0)
                        sets.get(i - 1).setDrawFilled(false);
                        sets.get(i).setDrawFilled(true);
                    } else {
                        sets.get(i).setDrawFilled(false);
                    }

                }while (sets.get(i).getYValForXIndex(j+1)!=ftarget);
                if (ftarget == sets.get(i).getYValForXIndex(j+1) && ftarget != sets.get(i).getYValForXIndex(j))
                    break;

            }

        }
        chart.invalidate();
    }





    public List<Map<String, Object>> encodecmd(String[] parameters) {
        //Encode the paramaters into commands for arduino
        int loopcut = 1; //This is for the arduino to cycle through some commands because there are some commands that only need to be done
        //at the beginning of the run  such as preheat, precool...
        int[] encoded = new int[parameters.length-1];
        for (int i = 0; i < encoded.length; i++) {
            if (parameters[i].isEmpty()) {
                encoded[i] = -1; //for when the entries are null ie. the user doesnt have a box checked
            } else
                encoded[i] = Integer.parseInt(parameters[i]);
        }
        List<Map<String, Object>> command = new ArrayList<>();

        Map<String, Object> map1OfList = new HashMap<>();
        map1OfList.put("type", "reset");
        command.add(map1OfList);

        if(encoded[0]!=-1) { //is there a precool stage?
            Map<String,Object>mapPreCool = new HashMap<>();
            if(parametersActivity[27].equals("Time(s):")) {//did user want the fan on for a specific amount of time?
                mapPreCool.put("type","precool");
                mapPreCool.put("time",encoded[0]);
                loopcut++;
            }
            else{
                mapPreCool.put("type","cool");
                mapPreCool.put("target",encoded[0]);
                loopcut++;
            }
            command.add(mapPreCool);
        }

        if(encoded[1]!= -1) { //is there a preheat stage?
            Map<String,Object>mapPreheat1OfList = new HashMap<>();
            mapPreheat1OfList.put("type","heat");
            mapPreheat1OfList.put("target",encoded[1]);
            command.add(mapPreheat1OfList);
            loopcut++;
            if(encoded[2]> 0 ) { //Does the temperature for preheat 1 need to be maintained?
                Map<String,Object>mapPreheat1ContOfList = new HashMap<>();
                mapPreheat1ContOfList.put("type","cont");
                mapPreheat1ContOfList.put("target",encoded[1]);
                mapPreheat1ContOfList.put("time", encoded[2]); //preheat time
                mapPreheat1ContOfList.put("kp", encoded[12]);
                mapPreheat1ContOfList.put("ki", encoded[13]);
                mapPreheat1ContOfList.put("kd", encoded[14]);
                command.add(mapPreheat1ContOfList);
                loopcut++;
            }
        }

        if(encoded[3]!=-1) { //is there a second preheat stage?
            Map<String,Object>mapPreheat2OfList = new HashMap<>();
            mapPreheat2OfList.put("type","heat");
            mapPreheat2OfList.put("target",encoded[3]);
            command.add(mapPreheat2OfList);
            loopcut++;
            if(encoded[4]>0) {// Does the temperature for preheat 2 need to be maintained?
                Map<String,Object> mapPreheat2ContOfList = new HashMap<>();
                mapPreheat2ContOfList.put("type","cont");
                mapPreheat2ContOfList.put("target",encoded[3]);
                mapPreheat2ContOfList.put("time",encoded[4]);
                mapPreheat2ContOfList.put("kp", encoded[15]);
                mapPreheat2ContOfList.put("ki", encoded[16]);
                mapPreheat2ContOfList.put("kd", encoded[17]);
                command.add(mapPreheat2ContOfList);
                loopcut++;

            }
        }


        Map<String, Object> map2OfList = new HashMap<>();
        map2OfList.put("type", "heat");
        map2OfList.put("target", encoded[5]); //denature
        command.add(map2OfList);
        if (encoded[6] > 0) { //denature time > 0s ? go to PID to maintain the temp
            Map<String, Object> map3OfList = new HashMap<>();
            map3OfList.put("type", "cont");
            map3OfList.put("target", encoded[5]); //denature temp
            map3OfList.put("time", encoded[6]); //denature time
            map3OfList.put("kp", encoded[18]);
            map3OfList.put("ki", encoded[19]);
            map3OfList.put("kd", encoded[20]);
            command.add(map3OfList);
        }
        if (encoded[5] >= encoded[7]) { //denature temp >= annealing temp?
            Map<String, Object> map4OfList = new HashMap<>();
            map4OfList.put("type", "cool");
            map4OfList.put("target", encoded[7]);
            command.add(map4OfList);
        } else { //annealing temp is actually greater than denature temp
            Map<String, Object> map4OfList = new HashMap<>();
            map4OfList.put("type", "heat");
            map4OfList.put("target", encoded[7]); // annealing temp
            command.add(map4OfList);
        }
        if (encoded[8]>0) {//annealing time greater than 0? go to PID
            Map<String,Object>annealingPID = new HashMap<>();
            annealingPID.put("type","cont");
            annealingPID.put("target",encoded[7]);
            annealingPID.put("time",encoded[8]);
            annealingPID.put("kp",encoded[21]);
            annealingPID.put("ki",encoded[22]);
            annealingPID.put("kd",encoded[23]);
            command.add(annealingPID);
        }
        if(encoded[9]!=-1) {
            if (encoded[9] > encoded[7]) { //extension temp greater than annealing temp?
                Map<String, Object> extension = new HashMap<>();
                extension.put("type", "heat");
                extension.put("target", encoded[9]); //extension temp
                command.add(extension);
            } else {
                Map<String, Object> extension = new HashMap<>();
                extension.put("type", "cool");
                extension.put("target", encoded[9]); //extension temp
                command.add(extension);
            }
            if (encoded[10] > 0) { //Extension time greater than 0?
                Map<String, Object> extensionPID = new HashMap<>();
                extensionPID.put("type", "cont");
                extensionPID.put("target", encoded[9]);
                extensionPID.put("time", encoded[10]);
                extensionPID.put("kp", encoded[24]);
                extensionPID.put("ki", encoded[25]);
                extensionPID.put("kd", encoded[26]);
                command.add(extensionPID);
            }
        }

        Map<String, Object> map5OfList = new HashMap<>();
        map5OfList.put("type", "loopback");
        map5OfList.put("amount", command.size() - loopcut);
        map5OfList.put("cycle", encoded[11]);
        map5OfList.put("init", 1);
        command.add(map5OfList);

        Map<String, Object> map6OfList = new HashMap<>();
        map6OfList.put("type", "end");
        command.add(map6OfList);
        /**WARNING the size of the list is not always going to be the same
         * the size depends on the parameters chosen */
        return command;
    }



    public void Run(final List<Map<String, Object>> cmd) {//this sends the commands to the arduino
        pushcmd( "R\n");//The wait commands allow the arduino to process and store the data for thermocycling
                for (int i = 0; i < cmd.size(); i++) {
                    if (cmd.get(i).get("type").equals("reset")) {
                        pushcmd("RC\n");
                        try {
                            TimeUnit.MILLISECONDS.sleep(130);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (cmd.get(i).get("type").equals("end")) {
                        pushcmd("EC\n");
                        try {
                            TimeUnit.MILLISECONDS.sleep(120);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (cmd.get(i).get("type").equals("loopback")) {
                        pushcmd(encnum(cmd.get(i).get("init")) + encnum(cmd.get(i).get("cycle")) + encnum(cmd.get(i).get("amount")) + "LC\n");
                        Body+= "\nRepeat last " + cmd.get(i).get("amount") + " commands for " + cmd.get(i).get("cycle") + " cycles ";
                        try {
                            TimeUnit.MILLISECONDS.sleep(120);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (cmd.get(i).get("type").equals("precool")) {
                        Body+= "\nPrecool for: " + cmd.get(i).get("time").toString() + " Seconds";
                        pushcmd(encnum(cmd.get(i).get("time")) + "KC\n");
                        try {
                            TimeUnit.MILLISECONDS.sleep(120);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (cmd.get(i).get("type").equals("heat")) {
                        Body+="\nHeat to: " +cmd.get(i).get("target").toString() +" degrees Celsius";
                        pushcmd(encnum(cmd.get(i).get("target")) + "HC\n");
                        try {
                            TimeUnit.MILLISECONDS.sleep(120);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (cmd.get(i).get("type").equals("cool")) {
                        pushcmd(encnum(cmd.get(i).get("target")) + "CC\n");
                        Body+="\nCool to: "+ cmd.get(i).get("target").toString() +" degrees Celsius";
                        try {
                            TimeUnit.MILLISECONDS.sleep(120);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (cmd.get(i).get("type").equals("cont")) {
                        Body+="\n      and maintain for " + cmd.get(i).get("time").toString() +" Seconds";
                        pushcmd(encnum(cmd.get(i).get("kd"))
                                + encnum(cmd.get(i).get("ki")) + encnum(cmd.get(i).get("kp")) +
                                encnum(cmd.get(i).get("time")) + encnum(cmd.get(i).get("target")) + "OC\n");
                        try {
                            TimeUnit.MILLISECONDS.sleep(180);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

         pushcmd("X\n");

    }







}