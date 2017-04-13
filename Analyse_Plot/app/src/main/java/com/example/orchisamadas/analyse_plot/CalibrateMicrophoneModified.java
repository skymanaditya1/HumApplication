package com.example.orchisamadas.analyse_plot;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * This activity calibrates an android device's microphone.
 * It makes a recording using 1. An external microphone.
 * 2. Using the android's microphone
 * After converting the data from time domain into frequency domain,
 * the gain for each frequency band is calculated.
 */

public class CalibrateMicrophoneModified extends AppCompatActivity {

    final int SAMPLING_RATE = 8000, CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_DEFAULT,
            AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT, RECORDING_DURATION = 5;
    final String EXTERNAL_AUDIO_FILENAME = "external_audio.wav", ANDROID_AUDIO_FILENAME = "android_audio.wav";
    AudioRecord recorder;
    Button startRecording, androidRecording, calculateFrequencyGain, calibrateResponse;
    ImageButton startPlayback;
    TextView timer;
    CounterClass counterClass = new CounterClass(RECORDING_DURATION * 1000, 1000);
    double[] externalMicValues, androidMicValues;
    final String EXTERNAL_MIC_RECORDING = "External Microphone",
            ANDROID_MIC_RECORDING = "Android Microphone";
    double[] frequencyGain, frequencyAveragedExternal, frequencyAveragedAndroid;
    double REFSPL = 0.00002; // Reference Sound Pressure Level equal to 20 uPa.
    final String DIRECTORY_NAME = "Hum_Application", GAIN_FILENAME = "frequency_gain_values.txt",
            EXTERNAL_FREQUENCY_RESPONSE = "external_frequency_response.txt", INTERNAL_FREQUENCY_RESPONSE = "internal_frequency_response.txt",
            FREQUENCY_RESPONSE_CALIBRATED = "frequency_response_calibrated.txt";
            // FREQUENCY_RESPONSE = "frequency_response_uncalibrated.txt",
    final String FREQUENCY_AVERAGES_INTERNAL = "internal_frequency_averages.txt",
            FREQUENCY_AVERAGES_EXTERNAL = "external_frequency_averages.txt";

    CaptureAudio captureAudio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate_microphone_modified);

        Toast.makeText(CalibrateMicrophoneModified.this, "Inside the modified calibration file", Toast.LENGTH_SHORT).show();

        startRecording = (Button) findViewById(R.id.button_start_recording);
        startPlayback = (ImageButton) findViewById(R.id.calibrate_playback_sound);
        androidRecording = (Button) findViewById(R.id.button_microphone_recording);
        calculateFrequencyGain = (Button) findViewById(R.id.button_frequency_gain);
        calibrateResponse = (Button) findViewById(R.id.button_calibrated_response);
        timer = (TextView) findViewById(R.id.textView_timer);

        // disable the button with the internal microphone recording
        androidRecording.setEnabled(false);
        externalMicrophoneRecording();
        androidMicrophoneRecording();

        calculateFrequencyGain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Method to calculate the frequency gain from the prerecorded values
                // from the external microphone and the internal android microphone
                calculateFrequencyGain();
                // Frequency Gain values needs to be saved to a file
                saveGainValuesToFile();
                Toast.makeText(CalibrateMicrophoneModified.this, "Frequency Gain has been calculated and saved to a file",
                        Toast.LENGTH_SHORT).show();
                calibrateResponse.setVisibility(View.VISIBLE);
            }
        });

        calibrateResponse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(CalibrateMicrophoneModified.this, "Frequency response has been calibrated", Toast.LENGTH_SHORT).show();
                File humDir = new File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME);
                if(!humDir.exists()) humDir.mkdir();
                File inputFile = new File(humDir + File.separator + INTERNAL_FREQUENCY_RESPONSE);
                File outputFile = new File(humDir + File.separator + FREQUENCY_RESPONSE_CALIBRATED);
                if(outputFile.exists()) outputFile.delete();
                if(!outputFile.exists()){
                    try{
                        PrintWriter printWriter = new PrintWriter(outputFile);
                        BufferedReader bufferedReader = new BufferedReader(new FileReader(inputFile));
                        printWriter.println(bufferedReader.readLine());
                        String line;
                        while((line=bufferedReader.readLine())!=null){
                            double frequency = Double.parseDouble(line.split("\t")[0]);
                            double amplitude = Double.parseDouble(line.split("\t")[1]);
                            double calibratedAmplitude = amplitude * frequencyGain[(int)frequency/25];
                            printWriter.println(Double.toString(frequency) + "\t" + Double.toString(calibratedAmplitude));
                        }
                        printWriter.flush();
                        printWriter.close();
                    } catch (IOException e){
                        System.out.println("Exception of type : " + e.toString());
                    }
                }
            }
        });
    }

    @Override
    protected void onPause() {
        if(captureAudio!=null) captureAudio.cancel(false);
        super.onPause();
        finish();
    }

    // Method to save the frequency gain values to a file
    public void saveGainValuesToFile(){
        File humDir = new File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME);
        if(!humDir.exists()) humDir.mkdir();
        File file = new File(humDir + File.separator + GAIN_FILENAME);
        System.out.println("File checked for existence : " + humDir + File.separator + GAIN_FILENAME);
        // Delete the file if it exists and obtain the calibration values again
        if(file.exists()) file.delete();
        if(!file.exists()){
            try {
                PrintWriter printWriter = new PrintWriter(file);
                int frequency_start = 0; // represents the start of the frequency range
                printWriter.println("Frequency Range (Hz) : Calibration Factor");
                for(double gainValue : frequencyGain){
                    printWriter.println(frequency_start + " - " + (frequency_start+25) + "\t" + Double.toString(gainValue));
                    frequency_start += 25;
                    // printWriter.println(Double.toString(gainValue));
                }
                printWriter.flush();
                printWriter.close();
            } catch(IOException e){
                System.out.println("Exception of type : " + e.toString());
            }
        } else{
            System.out.println("File already exists");
        }
    }

    public void saveNormalizedData(short[] sampleBuffer, String filename){
        File humDir = new File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME);
        if(!humDir.exists()) humDir.mkdir();
        File file = new File(humDir + File.separator + filename);
        System.out.println("File checked for existence : " + humDir + File.separator + filename);
        // Delete the file if it exists and obtain the calibration values again
        if(file.exists()) file.delete();
        if(!file.exists()){
            try {
                PrintWriter printWriter = new PrintWriter(file);
                for(short sample : sampleBuffer){
                    printWriter.println(sample);
                }
                printWriter.flush();
                printWriter.close();
            } catch(IOException e){
                System.out.println("Exception of type : " + e.toString());
            }
        } else{
            System.out.println("File already exists");
        }
    }

    public void saveNormalizedDataDouble(double[] sampleBuffer, String filename){
        File humDir = new File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME);
        if(!humDir.exists()) humDir.mkdir();
        File file = new File(humDir + File.separator + filename);
        System.out.println("File checked for existence : " + humDir + File.separator + filename);
        // Delete the file if it exists and obtain the calibration values again
        if(file.exists()) file.delete();
        if(!file.exists()){
            try {
                PrintWriter printWriter = new PrintWriter(file);
                for(double sample : sampleBuffer){
                    printWriter.println(sample);
                }
                printWriter.flush();
                printWriter.close();
            } catch(IOException e){
                System.out.println("Exception of type : " + e.toString());
            }
        } else{
            System.out.println("File already exists");
        }
    }

    public void calculateFrequencyGain(){
        // Calculate the frequency gain from the two recordings
        frequencyGain = new double[frequencyAveragedExternal.length];
        for(int i=0; i<frequencyAveragedExternal.length; i++)
            frequencyGain[i] = frequencyAveragedExternal[i] / frequencyAveragedAndroid[i];
        // System.out.println("The length of the frequency gain calculated is : " + frequencyGain.length);
    }

    public class CounterClass extends CountDownTimer{
        public CounterClass(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            String hms = String.format("%02d", TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)));
            timer.setText("Time remaining : " + hms);
        }

        @Override
        public void onFinish() {
            timer.setText("Captured Sound");
        }
    }

    public void externalMicrophoneRecording(){
        Toast.makeText(CalibrateMicrophoneModified.this,
                "Please insert an external microphone to continue", Toast.LENGTH_SHORT).show();
        startRecording.setVisibility(View.VISIBLE);
        startRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording.setEnabled(false);
                // new CaptureAudio().execute(EXTERNAL_MIC_RECORDING);
                captureAudio = new CaptureAudio();
                captureAudio.execute(EXTERNAL_MIC_RECORDING);
                androidRecording.setEnabled(true);
            }
        });
    }

    public void androidMicrophoneRecording(){
        Toast.makeText(CalibrateMicrophoneModified.this, "Continue to record using the Android microphone",
                Toast.LENGTH_SHORT).show();
        androidRecording.setVisibility(View.VISIBLE);
        androidRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                androidRecording.setEnabled(false);
                // new CaptureAudio().execute(ANDROID_MIC_RECORDING);
                captureAudio = new CaptureAudio();
                captureAudio.execute(ANDROID_MIC_RECORDING);
                // Enable the calculate frequency gain button
                calculateFrequencyGain.setVisibility(View.VISIBLE);
            }
        });
    }

    public class CaptureAudio extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_CONFIG, AUDIO_ENCODING);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLING_RATE,
                    CHANNEL_CONFIG, AUDIO_ENCODING, bufferSize);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(CalibrateMicrophoneModified.this, "Recording device initialization failed",
                        Toast.LENGTH_SHORT).show();
                recorder.release();
                recorder=null;
            }
        }

        @Override
        protected String doInBackground(String... params) {
            int samplesRead = 0;
            int sampleBufferLength = nearestPow2Length(SAMPLING_RATE * RECORDING_DURATION);
            short[] sampleBuffer = new short[sampleBufferLength];
            counterClass.start();
            recorder.startRecording();
            while (samplesRead < sampleBufferLength)
                samplesRead += recorder.read(sampleBuffer, samplesRead, sampleBufferLength - samplesRead);
            double max = calculateMax(sampleBuffer);

            Log.e("CALIBRATE_MICROPHONE", "The max value is : " + max);
            // the sample contents are saved before and after normalization for external mic recording
            // saveNormalizedData(sampleBuffer, "before_normalization.txt");
            if(params[0].equals(EXTERNAL_MIC_RECORDING)) {
                // Storing the values before normalization
                externalMicValues = normalizeTimeDomainData(sampleBuffer, max);
                // save the contents of the samples recorded after normalization
                // saveNormalizedDataDouble(externalMicValues, "after_normalization.txt");
                applyBasicWindow(externalMicValues);
                int error = doubleFFT(externalMicValues);
                for(int i=0; i<externalMicValues.length; i++) externalMicValues[i] /= REFSPL;

                Log.e("EXTERNAL_MIC_LENGTH", "The length of the external Microphone recording is : " +
                        externalMicValues.length);
            }
            else if (params[0].equals(ANDROID_MIC_RECORDING)){
                androidMicValues = normalizeTimeDomainData(sampleBuffer, max);
                applyBasicWindow(androidMicValues);
                int error = doubleFFT(androidMicValues);
                for(int i=0; i<androidMicValues.length; i++) androidMicValues[i] /= REFSPL;

                Log.e("INTERNAL_MIC_LENGTH", "The length of the internal Microphone recording is : " +
                        androidMicValues.length);
            }
            // Clear the recorder
            if (recorder != null) {recorder.release(); recorder=null;}
            if(params[0].equals(EXTERNAL_MIC_RECORDING)) saveRecording(sampleBuffer, EXTERNAL_AUDIO_FILENAME);
            else if (params[0].equals(ANDROID_MIC_RECORDING)) saveRecording(sampleBuffer, ANDROID_AUDIO_FILENAME);

            int samplesPerPoint = 32; // samples per point

            // Calculate the amplitude values as Samples_Per_Bin
            if(params[0].equals(EXTERNAL_MIC_RECORDING)){
                int width = externalMicValues.length / samplesPerPoint / 2;
                double maxYvalExternal = 0; // Stores the max amplitude (external microphone)
                // Stores the amplitude values (external microphone) in frequency domain
                double[] tempBufferExternal = new double[width];
                for(int k=0; k<tempBufferExternal.length; k++){
                    for(int n=0; n<samplesPerPoint; n++)
                        tempBufferExternal[k] += externalMicValues[k*samplesPerPoint + n];
                    tempBufferExternal[k] /= (double)samplesPerPoint;
                    if(maxYvalExternal < tempBufferExternal[k]) maxYvalExternal = tempBufferExternal[k];
                }

                // Stores the frequency values (external microphone)
                double[] xValsExternal = new double[tempBufferExternal.length];
                for(int k=0; k<xValsExternal.length; k++)
                    xValsExternal[k] = k * SAMPLING_RATE / (2*xValsExternal.length);

                // Method to write the frequency value and the corresponding amplitude
                File humDir = new File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME);
                if(!humDir.exists()) humDir.mkdir();
                File file = new File(humDir + File.separator + EXTERNAL_FREQUENCY_RESPONSE);
                // Delete the file if it exists already
                if(file.exists()) file.delete();
                if(!file.exists()){
                    try{
                        PrintWriter pWriter = new PrintWriter(file);
                        pWriter.println("Frequency : Amplitude");
                        for(int i=0; i<tempBufferExternal.length; i++){
                            pWriter.println(xValsExternal[i] + "\t" + tempBufferExternal[i]);
                        }
                        pWriter.flush();
                        pWriter.close();
                    } catch(IOException e){
                        System.out.println("Exception of type : " + e.toString());
                    }
                } else{
                    System.out.println("The file already exists");
                }

                // Write the frequency averages and amplitude to file
                File frequencyAveragesExternal = new File(humDir + File.separator + FREQUENCY_AVERAGES_EXTERNAL);
                // Calculates the frequency averages
                int j = 0;
                ArrayList<Double> frequencyAveraged =  new ArrayList<Double>();
                // Calculate the frequency gain in each frequency band
                if(frequencyAveragesExternal.exists()) frequencyAveragesExternal.delete();
                try{
                    PrintWriter printWriter = new PrintWriter(frequencyAveragesExternal);
                    printWriter.println("Frequency Range (Hz) : Average Amplitude ");
                    for(int i=0; i<SAMPLING_RATE/2; i+=25){
                        double temp = 0.0; int count = 0;
                        while(j<tempBufferExternal.length && xValsExternal[j] >= i && xValsExternal[j] < (i+25)) {
                            temp += tempBufferExternal[j];
                            count ++;
                            j++;
                        }
                        frequencyAveraged.add(temp/(double)count);
                        printWriter.println(i + " - " + (i+25) + "\t" + Double.toString(temp/(double)count));
                    }
                    printWriter.flush();
                    printWriter.close();
                } catch(IOException e){
                    System.out.println("Exception of type : " + e.toString());
                }


                // Convert the ArrayList into a double array
                // The frequency ranges are
                // (0-25) Hz - amplitude_average1
                // (25-50) Hz - amplitude_average2
                // (50-75) Hz - amplitude_average3
                // ...
                frequencyAveragedExternal = new double[frequencyAveraged.size()];
                for(int i=0; i<frequencyAveraged.size(); i++) frequencyAveragedExternal[i] = frequencyAveraged.get(i);
            }

            else if(params[0].equals(ANDROID_MIC_RECORDING)){
                int width = androidMicValues.length / samplesPerPoint / 2;
                double maxYvalAndroid = 0; // Stores the max amplitude (android microphone)
                // Stores the amplitude values (android microphone)
                double[] tempBufferAndroid = new double[width];

                for(int k=0; k<tempBufferAndroid.length; k++){
                    for(int n=0; n<samplesPerPoint; n++)
                        tempBufferAndroid[k] += androidMicValues[k*samplesPerPoint + n];
                    tempBufferAndroid[k] /= (double) samplesPerPoint;
                    if(maxYvalAndroid < tempBufferAndroid[k]) maxYvalAndroid = tempBufferAndroid[k];
                }

                // Stores the frequency values (android microphone)
                double[] xValsAndroid = new double[tempBufferAndroid.length];
                for(int k=0; k<xValsAndroid.length; k++)
                    xValsAndroid[k] = k * SAMPLING_RATE / (2*xValsAndroid.length);

                // Method to write the frequency value and the corresponding amplitude
                File humDir = new File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME);
                if(!humDir.exists()) humDir.mkdir();
                File file = new File(humDir + File.separator + INTERNAL_FREQUENCY_RESPONSE);
                // Delete the file if it exists already
                if(file.exists()) file.delete();
                if(!file.exists()){
                    try{
                        PrintWriter pWriter = new PrintWriter(file);
                        pWriter.println("Frequency : Amplitude");
                        for(int i=0; i<tempBufferAndroid.length; i++){
                            pWriter.println(xValsAndroid[i] + "\t" + tempBufferAndroid[i]);
                        }
                        pWriter.flush();
                        pWriter.close();
                    } catch(IOException e){
                        System.out.println("Exception of type : " + e.toString());
                    }
                } else{
                    System.out.println("The file already exists");
                }

                File androidFrequencyAverages = new File(humDir + File.separator + FREQUENCY_AVERAGES_INTERNAL);

                // Apply the frequency gain in each frequency band
                int j = 0;
                ArrayList<Double> frequencyAveraged = new ArrayList<Double>();
                // Calculate the frequency gain in each frequency band

                if(androidFrequencyAverages.exists()) androidFrequencyAverages.delete();
                try{
                    PrintWriter printWriter = new PrintWriter(androidFrequencyAverages);
                    printWriter.println("Frequency Range (Hz) : Average Amplitude ");
                    for(int i=0; i<SAMPLING_RATE/2; i+=25){
                        double temp = 0.0; int count = 0;
                        while(j<tempBufferAndroid.length && xValsAndroid[j] >= i && xValsAndroid[j] < (i+25)){
                            temp += tempBufferAndroid[j];
                            count++;
                            j++;
                        }
                        frequencyAveraged.add(temp/count);
                        printWriter.println(i + " - " + (i+25) + "\t" + Double.toString(temp/(double)count));
                    }
                    printWriter.flush();
                    printWriter.close();
                } catch(IOException e){
                    System.out.println("Exception of type : " + e.toString());
                }

                // Convert the Array List into a double array
                frequencyAveragedAndroid = new double[frequencyAveraged.size()];
                for(int i=0; i<frequencyAveraged.size(); i++)  frequencyAveragedAndroid[i] = frequencyAveraged.get(i);
            }

            return params[0];
        }

        @Override
        protected void onPostExecute(String aVoid) {
            final String fileName = aVoid;
            startPlayback.setVisibility(View.VISIBLE);
            startPlayback.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(fileName.equals(EXTERNAL_MIC_RECORDING))
                        playbackRecording(EXTERNAL_AUDIO_FILENAME);
                    else if(fileName.equals(ANDROID_MIC_RECORDING))
                        playbackRecording(ANDROID_AUDIO_FILENAME);
                }
            });
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);
        }
    }

    public int doubleFFT(double[] samples){
        double[] real = new double[samples.length], imag = new double[samples.length];
        System.arraycopy(samples, 0, real, 0, samples.length);
        for(int n=0; n<samples.length; n++) imag[n] = 0;
        int error = FFTbase.fft(real, imag, true);
        if(error==-1) return -1;
        for(int n=0; n<samples.length; n++) samples[n] = Math.sqrt(real[n]*real[n] + imag[n]*imag[n]);
        return 0;
    }

    public static void applyBasicWindow(double[] samples){
        samples[0] *= 0.0625;
        samples[1] *= 0.125;
        samples[2] *= 0.25;
        samples[3] *= 0.5;
        samples[4] *= 0.75;
        samples[5] *= 0.875;
        samples[6] *= 0.9375;

        samples[samples.length - 1] *= 0.0625;
        samples[samples.length - 2] *= 0.125;
        samples[samples.length - 3] *= 0.25;
        samples[samples.length - 4] *= 0.5;
        samples[samples.length - 5] *= 0.75;
        samples[samples.length - 6] *= 0.875;
        samples[samples.length - 7] *= 0.9375;
    }

    public double[] normalizeTimeDomainData(short[] sampleBuffer, double max){
        double[] samples = new double[sampleBuffer.length];
        for (int i=0; i<sampleBuffer.length; i++) samples[i] = sampleBuffer[i] / max;
        return samples;
    }

    public double calculateMax(short[] sampleBuffer){
        double max = 0.0;
        for (int i=0; i<sampleBuffer.length; i++)
            if (sampleBuffer[i] > max) max=sampleBuffer[i];
        return max;
    }

    public int nearestPow2Length(int length){
        int temp = (int) (Math.log(length) / Math.log(2.0) + 0.5); length = 1;
        for (int n=1; n<=temp; n++) length*=2;
        return length;
    }

    // Method for saving the recorded file into the phone's memory
    public void saveRecording(short[] sampleBuffer, String filename){
        /**File file = new File(Environment.getExternalStorageDirectory(),
         new SimpleDateFormat("yyyyMMddhhmmss'.wav'").format(new Date()));*/
        File file = new File(Environment.getExternalStorageDirectory(), filename);
        if (file.exists()) file.delete();
        try {
            file.createNewFile();
            DataOutputStream dataOutputStream =
                    new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            for(int n=0; n<sampleBuffer.length; n++) dataOutputStream.writeShort(sampleBuffer[n]);
        } catch (IOException e) {
        }
    }

    // Method for playing back the recorded audio
    public void playbackRecording(String filename){
        File file = new File(Environment.getExternalStorageDirectory(), filename);
        int audioLength = (int)file.length()/2;
        short[] audio = new short[audioLength];
        try {
            DataInputStream dataInputStream =
                    new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            int audioRead = 0;
            while (dataInputStream.available() > 0 ){
                audio[audioRead++] = dataInputStream.readShort();
            }
        } catch (IOException e) {
            System.out.println("Exception while playback record of type : " + e.toString());
        }

        // Create an AudioTrack object for playback
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLING_RATE,
                CHANNEL_CONFIG, AUDIO_ENCODING, audioLength, AudioTrack.MODE_STREAM);
        audioTrack.play();
        audioTrack.write(audio, 0, audioLength);
    }
}