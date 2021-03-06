package com.nuance.speechkitsample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.nuance.speechkit.Audio;
import com.nuance.speechkit.DetectionType;
import com.nuance.speechkit.Language;
import com.nuance.speechkit.Recognition;
import com.nuance.speechkit.RecognitionType;
import com.nuance.speechkit.Session;
import com.nuance.speechkit.Transaction;
import com.nuance.speechkit.TransactionException;


/**
 * This Activity is built to demonstrate how to perform ASR (Automatic Speech Recognition).
 *
 * This Activity is very similar to NLUActivity. Much of the code is duplicated for clarity.
 *
 * ASR is the transformation of speech into text.
 *
 * When performing speech recognition with SpeechKit, you have a variety of options. Here we demonstrate
 * Recognition Type (Language Model), Detection Type, and Language.
 *
 * Modifying the Recognition Type will help optimize your ASR results. Built in types are Dictation,
 * Search, and TV. Your choice will depend on your application. Each type will better understand some
 * words and struggle with others.
 *
 * Modifying the Detection Type will effect when the system thinks you are done speaking. Setting
 * this Long will allow your user to speak multiple sentences with short pauses in between. Setting
 * this to None will disable end of speech detection and will require you to tell the transaction
 * to stopRecording().
 *
 * Languages can also be configured. Supported languages can be found here:
 * http://developer.nuance.com/public/index.php?task=supportedLanguages
 *
 * Copyright (c) 2015 Nuance Communications. All rights reserved.
 */
public class ASRActivity extends DetailActivity implements View.OnClickListener {

    private static final String TAG = "myApp";

    private Audio startEarcon;
    private Audio stopEarcon;
    private Audio errorEarcon;

    private RadioGroup recoType;
    private RadioGroup detectionType;
    private EditText language;

    private TextView logs;
    private Button clearLogs;

    private Button toggleReco;

    private ProgressBar volumeBar;

    private Session speechSession;
    private Transaction recoTransaction;
    private State state = State.IDLE;
    
    private String[] happyVideo = {"eDuRoPIOBjE","FeAcke_Zay8&index=11&list=PLB7E80B2F70A9A8CD","cimoNqiulUE","uxpDa-c-4Mc","I2bBZvSPpOo","4cfoLDnNGnY&list=PL4TrGu5rwzH9X4PdkQ_84FTTn8i__U2Ou","RubBzkZzpUA","cimoNqiulUE","vkSFh6HMUtQ", "4U8FzehonsE", "2lTB1pIg1y0"};
    private String[] sadVideo = {"DcQzATCJpBA","-zzP29emgpg","GxgqpCdOKak","Dxy574tBK5A","s2fmEZ3-W5I","OCWyR436n1Q", "8Wl-HSl386k", "T9M2VAvpTXE&list=PLB7E80B2F70A9A8CD&index=3"};
    private String[] loveVideo = {"QgL33XNLhu0","Xyv4Bjja8yc","1Ldzm7KGECI","olG0Nm0auK0","TRLSQDCkcaA", "watch?v=_iXN2sKr91s&list=PLB7E80B2F70A9A8CD", "bJPGUHqs9ZM&index=10&list=PLB7E80B2F70A9A8CD"};
    private String[] angryVideo ={"qfbPrCiReNw&index=12&list=PLB7E80B2F70A9A8CD","19DCJ73y9T0","bY4OJBGBOY4","qxjxEFm9-vk", "79AR0VC5wCA&list=PL4TrGu5rwzH9X4PdkQ_84FTTn8i__U2Ou&index=4", };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asr);

        recoType = (RadioGroup)findViewById(R.id.reco_type_picker);
        detectionType = (RadioGroup)findViewById(R.id.detection_type_picker );
        language = (EditText)findViewById(R.id.language);

        logs = (TextView)findViewById(R.id.logs);
        clearLogs = (Button)findViewById(R.id.clear_logs);
        clearLogs.setOnClickListener(this);

        toggleReco = (Button)findViewById(R.id.toggle_reco);
        toggleReco.setOnClickListener(this);

        volumeBar = (ProgressBar)findViewById(R.id.volume_bar);

        //Create a session
        speechSession = Session.Factory.session(this, Configuration.SERVER_URI, Configuration.APP_KEY);

        loadEarcons();

        setState(State.IDLE);
    }

    @Override
    public void onClick(View v) {
        if(v == clearLogs) {
            logs.setText("");
        } else if(v == toggleReco) {
            toggleReco();
        }
    }

    /* Reco transactions */

    private void toggleReco() {
        switch (state) {
            case IDLE:
                recognize();
                break;
            case LISTENING:
                stopRecording();
                break;
            case PROCESSING:
                cancel();
                break;
        }
    }

    /**
     * Start listening to the user and streaming their voice to the server.
     */
    private void recognize() {
        //Setup our Reco transaction options.
        Transaction.Options options = new Transaction.Options();
        options.setRecognitionType(resourceIDToRecoType(recoType.getCheckedRadioButtonId()));
        options.setDetection(resourceIDToDetectionType(detectionType.getCheckedRadioButtonId()));
        options.setLanguage(new Language(language.getText().toString()));
        options.setEarcons(startEarcon, stopEarcon, errorEarcon, null);

        //Start listening
        recoTransaction = speechSession.recognize(options, recoListener);
    }

    private Transaction.Listener recoListener = new Transaction.Listener() {
        @Override
        public void onStartedRecording(Transaction transaction) {
            logs.append("\nonStartedRecording");

            //We have started recording the users voice.
            //We should update our state and start polling their volume.
            setState(State.LISTENING);
            startAudioLevelPoll();
        }

        @Override
        public void onFinishedRecording(Transaction transaction) {
            logs.append("\nonFinishedRecording");

            //We have finished recording the users voice.
            //We should update our state and stop polling their volume.
            setState(State.PROCESSING);
            stopAudioLevelPoll();
        }

        @Override
        public void onRecognition(Transaction transaction, Recognition recognition) {
            logs.append("\nonRecognition: " + recognition.getText());

            String em = Emotion.getEmotion(recognition.getText());

            switch (em) {
                case "HAPPY":
                    Log.i(TAG, "Happy");
                    openVideo("Happy");
                    break;
                case "SAD":
                    Log.i(TAG, "Sad");
                    openVideo("Sad");
                    break;
                case "LOVE":
                    Log.i(TAG, "In Love");
                    openVideo("Love");
                    break;
                case "ANGRY":
                    Log.i(TAG, "Angry");
                    openVideo("Angry");
                    break;
                default:
                    Log.i(TAG, "Bling");

//Notification of a successful transaction. Nothing to do here.
            }

            //We have received a transcription of the users voice from the server.
            setState(State.IDLE);
        }

        @Override
        public void onSuccess(Transaction transaction, String s) {
            logs.append("\nonSuccess");

            //Notification of a successful transaction. Nothing to do here.
        }

        @Override
        public void onError(Transaction transaction, String s, TransactionException e) {
            logs.append("\nonError: " + e.getMessage() + ". " + s);

            //Something went wrong. Check Configuration.java to ensure that your settings are correct.
            //The user could also be offline, so be sure to handle this case appropriately.
            //We will simply reset to the idle state.
            setState(State.IDLE);
        }
    };

    /**
     * Stop recording the user
     */
    private void stopRecording() {
        recoTransaction.stopRecording();
    }

    /**
     * Cancel the Reco transaction.
     * This will only cancel if we have not received a response from the server yet.
     */
    private void cancel() {
        recoTransaction.cancel();
        setState(State.IDLE);
    }

    /* Audio Level Polling */

    private Handler handler = new Handler();

    /**
     * Every 50 milliseconds we should update the volume meter in our UI.
     */
    private Runnable audioPoller = new Runnable() {
        @Override
        public void run() {
            float level = recoTransaction.getAudioLevel();
            volumeBar.setProgress((int)level);
            handler.postDelayed(audioPoller, 50);
        }
    };

    /**
     * Start polling the users audio level.
     */
    private void startAudioLevelPoll() {
        audioPoller.run();
    }

    /**
     * Stop polling the users audio level.
     */
    private void stopAudioLevelPoll() {
        handler.removeCallbacks(audioPoller);
        volumeBar.setProgress(0);
    }


    /* State Logic: IDLE -> LISTENING -> PROCESSING -> repeat */

    private enum State {
        IDLE,
        LISTENING,
        PROCESSING
    }

    /**
     * Set the state and update the button text.
     */
    private void setState(State newState) {
        state = newState;
        switch (newState) {
            case IDLE:
                toggleReco.setText(getResources().getString(R.string.recognize));
                break;
            case LISTENING:
                toggleReco.setText(getResources().getString(R.string.listening));
                break;
            case PROCESSING:
                toggleReco.setText(getResources().getString(R.string.processing));
                break;
        }
    }

    /* Earcons */

    private void loadEarcons() {
        //Load all the earcons from disk
        startEarcon = new Audio(this, R.raw.sk_start, Configuration.PCM_FORMAT);
        stopEarcon = new Audio(this, R.raw.sk_stop, Configuration.PCM_FORMAT);
        errorEarcon = new Audio(this, R.raw.sk_error, Configuration.PCM_FORMAT);
    }

    /* Helpers */

    private RecognitionType resourceIDToRecoType(int id) {
        if(id == R.id.dictation) {
            return RecognitionType.DICTATION;
        }
        if(id == R.id.search) {
            return RecognitionType.SEARCH;
        }
        if(id == R.id.tv) {
            return RecognitionType.TV;
        }
        return null;
    }

    private DetectionType resourceIDToDetectionType(int id) {
        if(id == R.id.long_endpoint) {
            return DetectionType.Long;
        }
        if(id == R.id.short_endpoint) {
            return DetectionType.Short;
        }
        if(id == R.id.none) {
            return DetectionType.None;
        }
        return null;
    }
    
    public void openVideo(String emotion) {
        String id;

        if (emotion.equals("Happy")) {
            id = happyVideo[(int) Math.floor(Math.random() * happyVideo.length)];
        }
        if (emotion.equals("Sad")) {
            id = sadVideo[(int) Math.floor(Math.random() * sadVideo.length)];
        }
        if (emotion.equals("Love")) {
            id = loveVideo[(int) Math.floor(Math.random() * loveVideo.length)];
        }
        if (emotion.equals("Angry")){
            id = angryVideo[(int) Math.floor(Math.random() * angryVideo.length)];
        }
        else{
            id = "uxpDa-c-4Mc";
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://" + id));
        startActivity(intent);


    }

    public static void main(String[] args) {

        String em = Emotion.getEmotion("happy merry cheery angry sad ");

        switch (em) {
            case "HAPPY":
                System.out.printf("happy");
                break;
            case "SAD":
                System.out.printf("Sad");
                break;
            case "LOVE":
                System.out.printf("Love");
                break;
            case "ANGRY":
                System.out.printf("Angry");
                break;
            default:
                System.out.printf("Bling");
        }
    }


}
