package com.example.microsoft.getstartednh;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.microsoft.windowsazure.messaging.NotificationHub;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class RegistrationIntentService extends IntentService
{
    private static final String TAG = "RegIntentService";

    private NotificationHub hub;

    public RegistrationIntentService()
    {
        super(TAG);
    }

    private String HubEndpoint = null;
    private String HubSasKeyName = null;
    private String HubSasKeyValue = null;

    @Override
    protected void onHandleIntent(Intent intent)
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String installationId = null;

        try
        {
            if((installationId = sharedPreferences.getString("InstallationId", null)) == null)
            {
                // Для теста. На бою installationId нужно из сервиса.
                installationId = java.util.UUID.randomUUID().toString();

                sharedPreferences.edit().putString("InstallationId", installationId).apply();

                Log.d(TAG, "InstallationId: " + installationId);

                // Для теста
                if (MainActivity.isVisible)
                {
                    MainActivity.mainActivity.ShowInstallationId(installationId);
                }
            }

            String FCM_token = FirebaseInstanceId.getInstance().getToken();

            Log.d(TAG, "Saving NH Installation #" + installationId + "with FCM token: " + FCM_token);
            saveInstallation(installationId, FCM_token);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed to complete registration", e);
            // If an exception happens while fetching the new token or updating our registration data
            // on a third-party server, this ensures that we'll attempt the update at a later time.
        }
    }

    public void saveInstallation(String installationId, String FCM_token)
    {
        try
        {
            ParseConnectionString(NotificationSettings.HubListenConnectionString);
            URL url = new URL(HubEndpoint + NotificationSettings.HubName + "/installations/" + installationId + "?api-version=2015-01");

            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();

            try
            {
                urlConnection.setRequestMethod("PUT");

                JSONObject jsonObj = new JSONObject();
                jsonObj.put("installationId", installationId);
                jsonObj.put("platform", "gcm");
                jsonObj.put("pushChannel", FCM_token);
                String json = jsonObj.toString();

                urlConnection.setRequestProperty("ContentType", "application/json");
                urlConnection.setRequestProperty("Authorization", generateSasToken(url.toString()));
                urlConnection.setRequestProperty("x-ms-version", "2015-01");

                urlConnection.setFixedLengthStreamingMode(json.getBytes().length);

                OutputStream bodyStream = new BufferedOutputStream(urlConnection.getOutputStream());
                bodyStream.write(json.getBytes());
                bodyStream.close();

                urlConnection.connect();

                String responseMessage = urlConnection.getResponseMessage();
                int responseCode = urlConnection.getResponseCode();
                Log.d(TAG, "Installation response message: " + responseMessage);
                Log.d(TAG, "Installation response code: " + responseCode);
            }
            catch (JSONException e)
            {
                Log.e(TAG, "Failed to save installation", e);
            }
            finally
            {
                urlConnection.disconnect();
            }
        }
        catch (MalformedURLException e)
        {
            Log.e(TAG, "Failed to save installation", e);
        }
        catch (IOException e)
        {
            Log.e(TAG, "Failed to save installation", e);
        }
    }

    /**
     * Example code from http://msdn.microsoft.com/library/azure/dn495627.aspx
     * to parse the connection string so a SaS authentication token can be
     * constructed.
     *
     * @param connectionString This must be the DefaultFullSharedAccess connection
     *                         string for this example.
     */
    private void ParseConnectionString(String connectionString)
    {
        String[] parts = connectionString.split(";");
        if (parts.length != 3)
            throw new RuntimeException("Error parsing connection string: "
                    + connectionString);

        for (int i = 0; i < parts.length; i++)
        {
            if (parts[i].startsWith("Endpoint"))
            {
                this.HubEndpoint = "https" + parts[i].substring(11);
            }
            else if (parts[i].startsWith("SharedAccessKeyName"))
            {
                this.HubSasKeyName = parts[i].substring(20);
            }
            else if (parts[i].startsWith("SharedAccessKey"))
            {
                this.HubSasKeyValue = parts[i].substring(16);
            }
        }
    }

    /**
     * Example code from http://msdn.microsoft.com/library/azure/dn495627.aspx to
     * construct a SaS token from the access key to authenticate a request.
     *
     * @param uri The unencoded resource URI string for this operation. The resource
     *            URI is the full URI of the Service Bus resource to which access is
     *            claimed. For example,
     *            "http://<namespace>.servicebus.windows.net/<hubName>"
     */
    private String generateSasToken(String uri)
    {

        String targetUri;
        String token = null;

        try
        {
            targetUri = URLEncoder
                    .encode(uri.toString().toLowerCase(), "UTF-8")
                    .toLowerCase();

            long expiresOnDate = System.currentTimeMillis();
            int expiresInMins = 60*24; // 1 day
            expiresOnDate += expiresInMins * 60 * 1000;
            long expires = expiresOnDate / 1000;
            String toSign = targetUri + "\n" + expires;

            // Get an hmac_sha1 key from the raw key bytes
            byte[] keyBytes = HubSasKeyValue.getBytes("UTF-8");
            SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");

            // Get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);

            // Compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(toSign.getBytes("UTF-8"));

            // Using android.util.Base64 for Android Studio instead of
            // Apache commons codec
            String signature = URLEncoder.encode(
                    Base64.encodeToString(rawHmac, Base64.NO_WRAP).toString(), "UTF-8");

            // Construct authorization string
            token = "SharedAccessSignature sr=" + targetUri + "&sig="
                    + signature + "&se=" + expires + "&skn=" + HubSasKeyName;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Exception Generating SaS", e);
        }

        return token;
    }
}
