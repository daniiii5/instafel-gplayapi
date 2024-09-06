package me.mamiiblt.instafel.gplayapi;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import me.mamiiblt.instafel.gplayapi.utils.AppInfo;
import me.mamiiblt.instafel.gplayapi.utils.Log;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class Env {
    public static String email, aas_token, github_releases_link, github_pat, telegram_api_key;
    public static Properties devicePropertiesArm64, devicePropertiesArm32;
    public static OkHttpClient client;
    public static TelegramBot telegramBot;

    public static void updateEnvironment() {
        Properties prop = new Properties();
        client = new OkHttpClient();
        try {
            InputStream input = Main.class.getClassLoader().getResourceAsStream("gplayapi.properties");
            if (input == null) {
                Log.println("E", "Please configure gplayapi.properties file in resources and build jar.");
                System.exit(-1);
            }

            prop.load(input);

            String email_p = prop.getProperty("email", null);
            String aas_token_p = prop.getProperty("aas_token", null);
            String github_rel_link = prop.getProperty("github_releases_link", null);
            String github_pat_p = prop.getProperty("github_pat", null);
            String telegram_api_key_p = prop.getProperty("telegram_bot_token", null);

            if (email_p != null && aas_token_p != null && github_rel_link != null && github_pat_p != null && telegram_api_key_p != null) {
                email = email_p;
                aas_token = aas_token_p;
                github_releases_link = github_rel_link;
                github_pat = github_pat_p;
                telegram_api_key = telegram_api_key_p;
                telegramBot = new TelegramBot(telegram_api_key);

                Log.println("I", "User (" + email + ") read from config file.");

            } else {
                Log.println("E", "Error while reading email & aas token property from gplayapi.properties");
                System.exit(-1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("E", "Error while updating environment");
            System.exit(-1);
        }
    }

    public static void updateDeviceProp(String propNameArm64, String propNameArm32) {
        try {
            InputStream input64 = Main.class.getClassLoader().getResourceAsStream(Paths.get("device_props", propNameArm64).toString());
            InputStream input32 = Main.class.getClassLoader().getResourceAsStream(Paths.get("device_props", propNameArm32).toString());
            if (input64 == null || input32 == null) {
                Log.println("E", "Please write a valid property name");
                System.exit(-1);
            }

            devicePropertiesArm64 = new Properties();
            devicePropertiesArm64.load(input64);
            devicePropertiesArm32 = new Properties();
            devicePropertiesArm32.load(input32);

            Log.println("I", "Device " + devicePropertiesArm64.getProperty("UserReadableName") + " is set to Arm64");
            Log.println("I", "Device " + devicePropertiesArm32.getProperty("UserReadableName") + " is set to Arm32");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("E", "Error while updating device properties");
        }
    }

    public static void startChecker() {
        Timer timer = new Timer();


        final String[] lastCheckedVersion = {""};
        final int[] checkTime = {0};

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    checkTime[0]++;
                    Log.println("I", checkTime[0] + " check started.");
                    InstafelGplayapiInstance instanceArm64 = new InstafelGplayapiInstance("arm64", "com.instagram.android");
                    InstafelGplayapiInstance instanceArm32 = new InstafelGplayapiInstance("arm32", "com.instagram.android");

                    AppInfo appInfo64 = instanceArm64.getIgApk();
                    AppInfo appInfo32 = instanceArm32.getIgApk();

                    Log.println("I", "appInfoArm64: " + appInfo64.getRawJson());
                    Log.println("I", "appInfoArm32: " + appInfo32.getRawJson());

                    if (appInfo64.getVer_name().equals(appInfo32.getVer_name())) {
                        if (appInfo64.getVer_name().contains(".0.0.0.")) { // alpha version names always has this regex
                            if (!lastCheckedVersion[0].equals(appInfo64.getVer_name())) {
                                lastCheckedVersion[0] = appInfo64.getVer_name();
                                String latestIflVersion = getLatestInstafelVersion(); // get latest instafel version
                                if (latestIflVersion != null && latestIflVersion.equals(appInfo64.getVer_name())) { // this version released or not
                                    triggerRelease(appInfo64, appInfo32, latestIflVersion);
                                }
                            }
                        }
                    } else {
                        Log.println("E", "IG Versions are not same");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.println("E", "Error while checking ig updates.");
                }
            }
        };

        long delayMs = 900000; // check every 15 minutes
        timer.scheduleAtFixedRate(task, 0, delayMs);
    }


    private static void triggerRelease(AppInfo appInfo64, AppInfo appInfo32, String latestIflVersion) throws Exception {
        JSONObject releaseData = new JSONObject();
        releaseData.put("tag_name", "v" + appInfo64.getVer_name());
        releaseData.put("name", "Instagram " + appInfo64.getVer_name());

        Request request = new Request.Builder()
                .url(github_releases_link)
                .post(RequestBody.create(MediaType.parse("application/json"), releaseData.toString()))
                .addHeader("Authorization", "Bearer " + github_pat)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("Error while creating Github Release for v" + appInfo64.getVer_name() + "(code: " + response.body().string() + ")");

            JSONObject responseObject = new JSONObject(response.body().string());
            long releaseId = responseObject.getLong("id");

            uploadAssets(appInfo64, appInfo32, releaseId);
        }

    }

    private static void uploadAssets(AppInfo appInfo64, AppInfo appInfo32, long releaseId) throws Exception {
        Log.println("I","Downloading IG apks into /tmp/");
        // if file exists it override too
        FileUtils.copyURLToFile(new URL(appInfo64.getApkUrl()), new File("/tmp/ig_arm64.apk"));
        FileUtils.copyURLToFile(new URL(appInfo32.getApkUrl()), new File("/tmp/ig_arm32.apk"));
        Log.println("I","Downloaded IG apks");
        Log.println("I", "Uploading assets into release.");
        String linkArm64 = uploadAsset(appInfo64, releaseId);
        String linkArm32 = uploadAsset(appInfo32, releaseId);

        sendTelegramMessage(appInfo64, appInfo32, linkArm64, linkArm32);
    }

    private static void sendTelegramMessage(AppInfo appInfo64, AppInfo appInfo32, String linkArm64, String linkArm32) throws Exception {
        String chatId = "5469784776";
        String telegramMessage = "Instagram Alpha Release\n\n" +
                appInfo64.getVer_name() + " (" + appInfo64.getVer_code() + ") - " + convertToMb(appInfo64.getApkSize()) + " MB\n" +
                appInfo32.getVer_name() + " (" + appInfo32.getVer_code() + ") - " + convertToMb(appInfo32.getApkSize()) + " MB\n";
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Download 64-bit").url(linkArm64),
                        new InlineKeyboardButton("Download 32-bit").url(linkArm32)
                });
        SendMessage request = new SendMessage(chatId, telegramMessage)
                .parseMode(ParseMode.HTML)
                .replyMarkup(inlineKeyboard);

        SendResponse sendResponse = telegramBot.execute(request);
        if (sendResponse.isOk()) {
            Log.println("I", "Log message succesfully sended");
        } else {
            throw new Exception("Error while sending message into tg");
        }
    }

    private static String convertToMb(long apkSize) {
        double mbSize = apkSize / (1024.0 * 1024.0);
        String formattedSize = String.format("%.2f", mbSize);
        return formattedSize;
    }

    private static String uploadAsset(AppInfo appInfo, long releaseId) throws Exception {
        String assetName = "instagram_" + appInfo.getVer_name() + "_" + appInfo.getArch() + ".apk";

        File apkFile = new File("/tmp/ig_" + appInfo.getArch() + ".apk");
        Request request = new Request.Builder()
                .url("https://uploads.github.com/repos/daniiii5/instafel-gplayapi/releases/" + releaseId + "/assets?name=" + assetName)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("Authorization", "Bearer " + github_pat)
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .addHeader("Content-Type", "application/vnd.android.package-archive")
                .post(RequestBody.create(apkFile, MediaType.parse("application/vnd.android.package-archive")))

                .build();


        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("Error while uploading asset " + appInfo.getArch() + "(code: " + response.body().string() + ")");

            JSONObject responseObject = new JSONObject(response.body().string());
            return responseObject.getString("browser_download_url");
        }

    }

    private static String getLatestInstafelVersion() throws IOException, Exception {
        Request request = new Request.Builder()
                .url(github_releases_link + "/latest")
                .addHeader("Authorization", "Bearer " + github_pat)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("Instafel Release response code is " + response.code());

            JSONObject responseObject = new JSONObject(response.body().string());
            return responseObject.getString("tag_name").substring(1);
        }
    }

    private static boolean isNumeric(String part) {
        char[] chars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        for (int i = 0; i < chars.length; i++) {
            if (part.indexOf(chars[i]) != -1) {
                return true;
            }
        }

        return false;
    }
}