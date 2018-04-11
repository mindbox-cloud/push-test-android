package com.example.microsoft.getstartednh;

/**
 * Created by Wesley on 7/1/2016.
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;

import com.google.gson.Gson;
import com.microsoft.windowsazure.notifications.NotificationsHandler;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import android.os.AsyncTask;

public class MyHandler extends NotificationsHandler {
    public static final int NOTIFICATION_ID = 1;
    private NotificationManager mNotificationManager;
    Context ctx;

    public static final String MESSAGE_UNIQUE_KEY_EXTRA = "unique_key_extra";
    public static final String CLICKED_OBJECT_CODE_EXTRA = "click_object_code_extra";
    public static final String BUTTON_UNIQUE_KEY = "clicked_button_guid_extra";
    public static final String CLICK_URL_EXTRA = "redirect_url_extra";

    private class Button
    {
        String text;
        String url;
        String uniqueKey;
    }

    private class Message {
        String uniqueKey;
        String text;
        String imageUrl;
        String clickUrl;
        String payload;
        Button [] buttons;

        Message (String uniqueKey,
                String text,
                String imageUrl,
                String clickUrl,
                String payload,
                Button [] buttons){
            this.uniqueKey = uniqueKey;
            this.text = text ;
            this.imageUrl = imageUrl;
            this.clickUrl = clickUrl;
            this.payload = payload ;
            this.buttons = buttons;
        }
    }

    @Override
    public void onReceive(Context context, Bundle bundle) {
        ctx = context;

        Message message = new Message(
            bundle.getString("uniqueKey"),
            bundle.getString("message"),
            bundle.getString("imageUrl"),
            bundle.getString("clickUrl"),
            bundle.getString("payload"),
            new Gson().fromJson( bundle.getString("buttons"), Button[].class));

        try {
            sendNotification(message);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }

        if (MainActivity.isVisible) {
            MainActivity.mainActivity.ToastNotify(message.text);
        }
    }

    private void sendNotification(Message message)
            throws ExecutionException, InterruptedException {

        mNotificationManager = (NotificationManager)ctx
                .getSystemService(Context.NOTIFICATION_SERVICE);

        Bitmap picture = new RetrieveImageTask().execute(message.imageUrl).get();

        Notification.Style style = picture != null
            ? new Notification.BigPictureStyle().bigPicture(picture)
            : new Notification.BigTextStyle();

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Notification.Builder mBuilder =
                new Notification
                        .Builder(ctx)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setSmallIcon(R.drawable.common_full_open_on_phone)
                        .setStyle(style)
                        .setSound(defaultSoundUri)
                        .setContentText(message.text)
                        .setContentIntent(
                                MakeClickIntent(
                                        message.uniqueKey,
                                        message.clickUrl,
                                        null,
                                        0))
                        .setAutoCancel(false);

        Button[] buttons = message.buttons;
        Button button;
        if (buttons != null && buttons.length > 0)
        {
            button = buttons[0];
            mBuilder.addAction(
                    new Notification.Action
                            .Builder(
                                R.mipmap.ic_launcher,
                                buttons[0].text,
                                MakeClickIntent(
                                        message.uniqueKey,
                                        button.url,
                                        button.uniqueKey,
                                        1))
                            .build());

            if (buttons.length > 1)
            {
                button = buttons[1];
                mBuilder.addAction(
                        new Notification.Action
                                .Builder(
                                R.mipmap.ic_launcher,
                                button.text,
                                MakeClickIntent(
                                        message.uniqueKey,
                                        button.url,
                                        button.uniqueKey,
                                        2))
                                .build());

                if (buttons.length > 2)
                {
                    button = buttons[2];
                    mBuilder.addAction(
                        new Notification.Action
                                .Builder(
                                R.mipmap.ic_launcher,
                                button.text,
                                MakeClickIntent(
                                        message.uniqueKey,
                                        button.url,
                                        button.uniqueKey,
                                        3))
                                .build());
                }
            }
        }

        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    /**
     * Redirect to {@link MobilePushClickActivity}
     * @param messageUniqueKey Mobile push unique identifier.
     * @param clickUrl URL corresponding to the clicked object.
     * @param buttonUniqueKey Clicked button unique identifier. {@code null} if it is the body that has been clicked.
     * @param clickedObjectCode Code of the clicked object
     *                          (0 - Body, 1 - Button #1, 2 - Button #2, 3 - Button #3).
     *                          Required only to display nicely what object has been clicked.
     * @return
     */
    private PendingIntent MakeClickIntent (
            String messageUniqueKey,
            String clickUrl,
            String buttonUniqueKey,
            int clickedObjectCode){
        Intent intent = new Intent(ctx, MobilePushClickActivity.class);
        intent.putExtra(MESSAGE_UNIQUE_KEY_EXTRA, messageUniqueKey);
        intent.putExtra(CLICKED_OBJECT_CODE_EXTRA, clickedObjectCode);
        intent.putExtra(BUTTON_UNIQUE_KEY, buttonUniqueKey);
        intent.putExtra(CLICK_URL_EXTRA, clickUrl);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                ctx,
                clickedObjectCode,
                intent,
                PendingIntent.FLAG_ONE_SHOT);

        return pendingIntent;
    }

    private class RetrieveImageTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... url) {
            try {
                return BitmapFactory.decodeStream((InputStream)new URL(url[0]).getContent());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
