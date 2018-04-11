package com.example.microsoft.getstartednh;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.UUID;

public class MobilePushClickActivity extends AppCompatActivity {

    // Change Endpoint Id to your assigned value.
    private static final String ENDPOINT_EXTERNAL_ID = "${ENDPOINT_EXTERNAL_ID}";

    private static final String SCHEME = "https";
    private static final String API_ADDRESS = "api.mindbox.ru";

    private static final String ENDPOINT_ID_PARAMETER_NAME = "endpointId";
    private static final String CLICK_REGISTER_PATH = "v3/mobile-push/click";

    private static final HashMap<Integer, String> NAME_BY_CODE = new HashMap<Integer, String>() {{
        put(0, "BODY");
        put(1, "BUTTON #1");
        put(2, "BUTTON #2");
        put(3, "BUTTON #3");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_click);

        processIntent(getIntent());
    }

    private void processIntent(Intent intent) {

        final UUID messageGuid = UUID.fromString(intent.getStringExtra(MyHandler.MESSAGE_UNIQUE_KEY_EXTRA));

        final String buttonGuidString = intent.getStringExtra(MyHandler.BUTTON_UNIQUE_KEY);
        final UUID buttonGuid = buttonGuidString == null ? null : UUID.fromString(buttonGuidString);
        final int clickedObjectCode = intent.getIntExtra(MyHandler.CLICKED_OBJECT_CODE_EXTRA, -1);
        final String clickUrl = intent.getStringExtra(MyHandler.CLICK_URL_EXTRA);

        ((TextView) findViewById(R.id.txtHeader)).setText("REGISTERED CLICK");

        registerClick(new ClickRegisterRequestDto(messageGuid, buttonGuid));

        ((TextView) findViewById(R.id.txtClickedObject)).setText(NAME_BY_CODE.get(clickedObjectCode));
        ((TextView) findViewById(R.id.txtMessageKey)).setText(messageGuid.toString());
        ((TextView) findViewById(R.id.txtClickUrl)).setText(clickUrl);
    }

    /**
     * Register mobile push click by sending {@link ClickRegisterRequestDto} to Mindbox API.
     *
     * @param clickDto Data transfer object required by Mindbox API.
     */
    private void registerClick(final ClickRegisterRequestDto clickDto) {
        new Thread(new Runnable() {
            @Override
            public void run() {

                URL url = null;
                HttpURLConnection conn = null;
                try {
                    url = new URL(
                            new Uri.Builder()
                                    .scheme(SCHEME)
                                    .encodedAuthority(API_ADDRESS)
                                    .path(CLICK_REGISTER_PATH)
                                    .appendQueryParameter(ENDPOINT_ID_PARAMETER_NAME, ENDPOINT_EXTERNAL_ID)
                                    .build()
                                    .toString());


                    conn = (HttpURLConnection) url.openConnection();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    final String CLICK_TAG = "CLICK";

                    conn.setRequestMethod("POST");

                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    String json = new Gson().toJson(clickDto);

                    Log.i(CLICK_TAG, json);

                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(json.toString());

                    os.flush();
                    os.close();

                    Log.i(CLICK_TAG, String.valueOf(conn.getResponseCode()));
                    Log.i(CLICK_TAG, conn.getResponseMessage());

                    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));

                        StringBuilder result = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            result.append(line);
                        }

                        Log.i(CLICK_TAG, result.toString());

                        final ErrorResponseDto errorResponse = new Gson()
                                .fromJson(result.toString(), ErrorResponseDto.class);

                        runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(
                                        getBaseContext(),
                                        MessageFormat.format(
                                                "Status: {0}. {1}",
                                                errorResponse.httpStatusCode,
                                                errorResponse.errorMessage),
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        });
                    }
                } catch (Exception e) {

                    e.printStackTrace();
                }
                finally {
                    conn.disconnect();
                }
            }
        }).start();
    }

    /**
     * Click DTO - contains information about click required by Mindbox API.
     */
    private class ClickRegisterRequestDto {

        ClickDto click;

        /**
         * @param messageUniqueKey Mobile push unique  identifier.
         * @param buttonUniqueKey  Clicked button unique identifier. {@code null} if it is the body that has been clicked.
         */
        ClickRegisterRequestDto(UUID messageUniqueKey, UUID buttonUniqueKey) {
            click = new ClickDto();
            click.messageUniqueKey = messageUniqueKey;
            click.buttonUniqueKey = buttonUniqueKey;
        }

        private class ClickDto {
            UUID messageUniqueKey;
            UUID buttonUniqueKey;
        }
    }

    private class ErrorResponseDto {
        String status;
        String errorMessage;
        String errorId;
        String httpStatusCode;
    }
}
