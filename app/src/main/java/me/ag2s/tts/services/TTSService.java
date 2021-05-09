package me.ag2s.tts.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;
import android.util.Log;

import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import me.ag2s.tts.APP;
import me.ag2s.tts.utils.CommonTool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class TTSService extends TextToSpeechService {

    private static final String TAG = TTSService.class.getSimpleName();

    public static final String USE_CUSTOM_LANGUAGE = "use_custom_language";
    public static final String CUSTOM_LANGUAGE = "custom_language";

    public SharedPreferences sharedPreferences;
    private OkHttpClient client;
    private WebSocket webSocket;
    private boolean isSynthesizing;
    private static List<TtsActor> languages;
    private List<Voice> voices;
    public final static String[] supportedLanguages = {"deu-DEU", "eng-AUS", "eng-CAN", "eng-GBR", "eng-IND", "eng-USA", "spa-ESP", "spa-MEX", "fra-CAN", "fra-FRA", "hin-IND", "ita-ITA", "jpn-JPN", "kor-KOR", "nld-NLD", "pol-POL", "por-BRA", "rus-RUS", "tur-TUR", "zho-CHN", "zho-HKG", "zho-TWN"};

    public final static String[] supportVoiceNames = {"de-DE-KatjaNeural", "en-AU-NatashaNeural", "en-CA-ClaraNeural", "en-GB-MiaNeural", "en-IN-NeerjaNeural", "en-US-AriaNeural", "en-US-GuyNeural", "es-ES-ElviraNeural", "es-MX-DaliaNeural", "fr-CA-SylvieNeural", "fr-FR-DeniseNeural", "hi-IN-SwaraNeural", "it-IT-ElsaNeural", "ja-JP-NanamiNeural", "ko-KR-SunHiNeural", "nl-NL-ColetteNeural", "pl-PL-ZofiaNeural", "pt-BR-FranciscaNeural", "ru-RU-SvetlanaNeural", "tr-TR-EmelNeural", "zh-CN-XiaoxiaoNeural", "zh-CN-YunyangNeural", "zh-HK-HiuGaaiNeural", "zh-TW-HsiaoYuNeural"};

    public final static String[] supportVoiceLocales = {"de_DE", "en_AU", "en_CA", "en_GB", "en_IN", "en_US", "en_US", "es_ES", "es_MX", "fr_CA", "fr_FR", "hi_IN", "it_IT", "ja_JP", "ko_KR", "nl_NL", "pl_PL", "pt_BR", "ru_RU", "tr_TR", "zh_CN", "zh_CN", "zh_HK", "zh_TW"};

    public final static String[] supportVoiceVariants = {"Female", "Female", "Female", "Female", "Female", "Female", "Male", "Female", "Female", "Female", "Female", "Female", "Female", "Female", "Female", "Female", "Female", "Female", "Female", "Female", "Female", "Male", "Female", "Female"};
    private volatile String[] mCurrentLanguage = null;
    /*
     * This is the sampling rate of our output audio. This engine outputs
     * audio at 16khz 16bits per sample PCM audio.
     */
    private static final int SAMPLING_RATE_HZ = 16000;

    public TTSService() {
    }

    /**
     * 获取或者创建WS
     *
     * @return
     */
    public WebSocket getOrCreateWs(SynthesisCallback callback) {
        String url = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4";
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36 Edg/90.0.818.56")
                .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                //.addHeader("Sec-WebSocket-Key", "vZ8qxy8q/+2qpzpnhFmgQA==")
                .addHeader("Sec-WebSocket-Version", "13")
                //.addHeader("Sec-WebSocket-Extensions","permessage-deflate; client_max_window_bits")
                .addHeader("Sec-WebSocket-Accept", sharedPreferences.getString("Sec-WebSocket-Accept", "n6OeLXUK+jnjNCyRI3wmP10OFDc="))
                .build();
        this.webSocket = client.newWebSocket(request, new WebSocketListener() {


            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                super.onClosed(webSocket, code, reason);
                Log.v(TAG, "onClosed" + reason);
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                super.onClosing(webSocket, code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                super.onFailure(webSocket, t, response);
                Log.v(TAG, "onFailure", t);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                super.onMessage(webSocket, text);
                String endTag = "turn.end";
                String startTag = "turn.start";
                int endIndex = text.lastIndexOf(endTag);
                int startIndex = text.lastIndexOf(startTag);
                //生成开始
                if (startIndex != -1) {
                    isSynthesizing = true;
                }
                //生成结束
                if (endIndex != -1) {
                    isSynthesizing = false;
                    callback.done();
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                super.onMessage(webSocket, bytes);
                //音频数据流标志头
                String audioTag = "Path:audio\r\n";
                int audioIndex = bytes.lastIndexOf(audioTag.getBytes(StandardCharsets.UTF_8));
                if (audioIndex != -1) {
                    try {
                        //PCM数据
                        ByteString data = bytes.substring(audioIndex + audioTag.length());
                        int length = data.toByteArray().length;
                        //最大BufferSize
                        final int maxBufferSize = callback.getMaxBufferSize();
                        int offset = 0;
                        while (offset < data.toByteArray().length) {
                            int bytesToWrite = Math.min(maxBufferSize, length - offset);
                            Log.d(TAG, "maxBufferSize" + maxBufferSize +
                                    "data.length - offset" + (length - offset));
                            callback.audioAvailable(data.toByteArray(), offset, bytesToWrite);
                            offset += bytesToWrite;
                        }

                    } catch (Exception e) {
                        Log.d(TAG, "onMessage Error:", e);

                        //如果出错返回错误
                        callback.error();
                        isSynthesizing = false;
                    }

                }
            }


            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                super.onOpen(webSocket, response);
                //更新 Sec-WebSocket-Accept
                String SecWebSocketAccept = response.header("Sec-WebSocket-Accept");
                if (SecWebSocketAccept != null && !SecWebSocketAccept.isEmpty()) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    Log.d(TAG, "SSS:" + SecWebSocketAccept);
                    editor.putString("Sec-WebSocket-Accept", SecWebSocketAccept);
                    editor.apply();
                }
                Log.d(TAG, "onOpen" + response.headers().toString());
            }
        });


        return webSocket;
    }

    //发送合成语音配置
    private void sendConfig(TtsConfig ttsConfig) {
        String msg = "X-Timestamp:+" + getTime() + "\r\n" +
                "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n"
                + ttsConfig.toString();
        webSocket.send(msg);
    }

    /**
     * 获取时间戳
     *
     * @return String time
     */
    public String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (中国标准时间)", Locale.ENGLISH);
        Date date = new Date();
        return sdf.format(date);
    }

    /**
     * 发送合成text请求
     *
     * @param request 需要合成的txt
     */
    public void sendText(SynthesisRequest request, SynthesisCallback callback) {

        webSocket = getOrCreateWs(callback);
        sendConfig(new TtsConfig.Builder().sentenceBoundaryEnabled(true).build());
        String text = request.getCharSequenceText().toString();
        int pitch = request.getPitch();
        int rate = request.getSpeechRate();


        String RequestId = "868727dfbb97961edd36361dd7e4044c";
        String name = "zh-CN-XiaoxiaoNeural";
        name = request.getVoiceName();
        String time = getTime();
        if (sharedPreferences.getBoolean(USE_CUSTOM_LANGUAGE, false) && request.getLanguage().equals("zho")) {
            name = sharedPreferences.getString(CUSTOM_LANGUAGE, "zh-CN-XiaoxiaoNeural");
        }

        RequestId = CommonTool.getMD5String(text + time);


        Log.d(TAG, "SSS:" + request.getVoiceName());
        Log.d(TAG, "SSS:" + CommonTool.getMD5String(time));

        String sb = "X-RequestId:" + RequestId + "\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
                "X-Timestamp:" + time + "Z\r\n" +
                "Path:ssml\r\n\r\n" +
                "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice  name='" + name + "'>" +
                "<prosody pitch='+" + (pitch - 100) + "Hz' " +
                "rate ='+" + (rate - 100) + "%' " +
                "volume='+" + 0 + "%'>" +
                "<express-as role='OlderAdultMale' style='lyrical' styledegree='2' >" + text + "</express-as>" +
                "</prosody></voice></speak>" +
                "";
        Log.d(TAG, "SSS:" + sb);
        webSocket.send(sb);
    }


    @Override
    public void onCreate() {
        super.onCreate();
        client = APP.getBootClient().newBuilder()
                .cookieJar(new PersistentCookieJar(new SetCookieCache(),
                        new SharedPrefsCookiePersistor(getApplicationContext())))
                .build();
        sharedPreferences = getApplicationContext().getSharedPreferences("TTS", Context.MODE_PRIVATE);


    }


    public static int getIsLanguageAvailable(String lang, String country, String variant) {
        for (String lan : supportedLanguages) {
            if ((lang + "-" + country).equals(lan)) {
                return TextToSpeech.LANG_COUNTRY_AVAILABLE;
            }
        }
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }


    /**
     * 是否支持该语言。语言通过lang、country、variant这三个Locale的字段来表示，意思分别是语言、国家和地区，
     * 比如zh-CN表示大陆汉语。这个方法看着简单，但我在这里栽坑了好久，就是因为对语言编码标准（ISO 639-1、ISO 639-2）不熟悉。
     */
    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return getIsLanguageAvailable(lang, country, variant);

    }

    /**
     * 获取当前引擎所设置的语言信息，返回值格式为{lang,country,variant}。
     *
     * @return
     */
    @Override
    protected String[] onGetLanguage() {
        // Note that mCurrentLanguage is volatile because this can be called from
        // multiple threads.

        return mCurrentLanguage;
    }


    @Override
    public List<Voice> onGetVoices() {
        return super.onGetVoices();
    }


    @Override
    protected Set<String> onGetFeaturesForLanguage(String lang, String country, String variant) {
        HashSet<String> hashSet = new HashSet<>();
        hashSet.add(lang);
        hashSet.add(country);
        hashSet.add(variant);
        return hashSet;
    }

    public List<String> getVoiceNames(String lang, String country, String variant) {

        boolean found = false;
        List<String> vos = new ArrayList<>();
        for (int i = 0; i < supportVoiceLocales.length; i++) {
            String[] temp = supportVoiceLocales[i].split("_");
            Locale locale = new Locale(temp[0], temp[1], supportVoiceVariants[i]);
            Log.d(TAG, "getVoiceNames11" + locale.getISO3Language() + "-" + locale.getISO3Country() + "-" + locale.getVariant() + "-");
            Log.d(TAG, "getVoiceNames22" + lang + "-" + country + "-" + variant + "-");

            if (locale.getISO3Language().equals(lang) && locale.getISO3Country().equals(country) && locale.getVariant().equals(variant)) {
                vos.add(0, supportVoiceNames[i]);
                found = true;
            } else if (locale.getISO3Language().equals(lang) && locale.getISO3Country().equals(country)) {
                if (found) {
                    vos.add(supportVoiceNames[i]);
                } else {
                    vos.add(0, supportVoiceNames[i]);
                }

            } else if (locale.getISO3Language().equals(lang)) {
                vos.add(supportVoiceNames[i]);
            }
        }
        Log.d(TAG, "getVoiceNames" + lang + "-" + country + "-" + variant + "-" + vos.toString());
        return vos;
    }

    @Override
    public int onIsValidVoiceName(String voiceName) {
        for (String vn : supportVoiceNames) {
            if (voiceName.equals(vn)) {
                return TextToSpeech.SUCCESS;
            }
        }
        return TextToSpeech.SUCCESS;
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        String name = "zh-CN-XiaoxiaoNeural";
        if (variant.isEmpty()) {
            variant = "Female";
        }
        List<String> names = getVoiceNames(lang, country, variant);
        if (names.size() > 0) {
            name = names.get(0);
        }
        //name="zh-cn-XiaoyouNeural";

        return name;
    }


    @Override
    public int onLoadVoice(String voiceName) {
        return TextToSpeech.SUCCESS;
    }

    /**
     * 设置该语言，并返回是否是否支持该语言。
     * Note that this method is synchronized, as is onSynthesizeText because
     * onLoadLanguage can be called from multiple threads (while onSynthesizeText
     * is always called from a single thread only).
     */
    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        int result = onIsLanguageAvailable(lang, country, variant);
        if (result == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            mCurrentLanguage = new String[]{lang, country, variant};
        }

        return result;
    }

    /**
     * 停止tts播放或合成。
     */
    @Override
    protected void onStop() {
        isSynthesizing = false;
    }


    /**
     * 将指定的文字，合成为tts音频流
     *
     * @param request
     * @param callback
     */
    @SuppressLint("WrongConstant")
    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        // Note that we call onLoadLanguage here since there is no guarantee
        // that there would have been a prior call to this function.
        int load = onLoadLanguage(request.getLanguage(), request.getCountry(),
                request.getVariant());
        // We might get requests for a language we don't support - in which case
        // we error out early before wasting too much time.
        if (load == TextToSpeech.LANG_NOT_SUPPORTED) {
            callback.error(TextToSpeech.ERROR_INVALID_REQUEST);
            return;
        }
        // At this point, we have loaded the language we need for synthesis and
        // it is guaranteed that we support it so we proceed with synthesis.

        // We denote that we are ready to start sending audio across to the
        // framework. We use a fixed sampling rate (16khz), and send data across
        // in 16bit PCM mono.
        callback.start(SAMPLING_RATE_HZ,
                AudioFormat.ENCODING_PCM_16BIT, 1 /* Number of channels. */);
        sendText(request, callback);
        isSynthesizing = true;
        while (isSynthesizing) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


    }


}