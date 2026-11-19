package com.mexo.music;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/* v3 - YouTube stream resolver (app ke ANDAR).
 *
 * YouTube ka audio link koi hosted server (Netlify) nahi nikaal sakta -
 * YouTube datacenter IP ko bot bolke block kar deta hai. Isliye link
 * yahin phone se nikalta hai: phone ke mobile/wifi IP se request jaati
 * hai, jaise ViMusic/NewPipe apps karte hain.
 *
 * Kaam NewPipeExtractor karta hai (TeamNewPipe ki maintained library):
 * videoId -> StreamExtractor -> audio streams (nsig/signature decipher
 * Rhino JS engine se library khud karti hai). Hum sabse achhi quality
 * wali seedhi (progressive) audio stream chun ke uska URL dete hain.
 * MediaPlayer usi URL ko bajata hai.
 */
final class YtStreamResolver {

    private static boolean inited = false;

    private static synchronized void ensureInit() {
        if (!inited) {
            NewPipe.init(new OkHttpDownloader(),
                    new Localization("en", "IN"), new ContentCountry("IN"));
            inited = true;
        }
    }

    /* videoId ke liye bajne layak audio URL, ya null agar na mile.
       Background thread par hi chalana (network + JS decipher hota hai). */
    static String resolve(String videoId) throws Exception {
        ensureInit();
        StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=" + videoId);
        extractor.fetchPage();
        List<AudioStream> streams = extractor.getAudioStreams();
        AudioStream bestM4a = null, bestAny = null;
        for (AudioStream a : streams) {
            if (!a.isUrl() || a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            String content = a.getContent();
            if (content == null || content.isEmpty()) continue;
            if (bestAny == null || a.getAverageBitrate() > bestAny.getAverageBitrate()) bestAny = a;
            if (a.getFormat() == MediaFormat.M4A
                    && (bestM4a == null || a.getAverageBitrate() > bestM4a.getAverageBitrate())) {
                bestM4a = a;
            }
        }
        /* M4A pehle - Android MediaPlayer use aaram se baja leta hai */
        AudioStream pick = bestM4a != null ? bestM4a : bestAny;
        return pick == null ? null : pick.getContent();
    }

    /* NewPipeExtractor ko ek Downloader chahiye - ye OkHttp wala hai,
       bilkul NewPipe app ke DownloaderImpl jaisa seedha version. */
    static final class OkHttpDownloader extends Downloader {
        private final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();

        @Override
        public Response execute(Request request) throws IOException, ReCaptchaException {
            okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                    .url(request.url());
            String contentType = null;
            if (request.headers() != null) {
                for (Map.Entry<String, List<String>> e : request.headers().entrySet()) {
                    if ("Content-Type".equalsIgnoreCase(e.getKey())) {
                        if (!e.getValue().isEmpty()) contentType = e.getValue().get(0);
                        continue;
                    }
                    for (String v : e.getValue()) builder.addHeader(e.getKey(), v);
                }
            }
            String method = request.httpMethod() == null ? "GET" : request.httpMethod();
            if ("POST".equalsIgnoreCase(method)) {
                byte[] data = request.dataToSend() == null ? new byte[0] : request.dataToSend();
                builder.post(RequestBody.create(data,
                        contentType == null ? null : MediaType.parse(contentType)));
            } else if ("HEAD".equalsIgnoreCase(method)) {
                builder.head();
            } else {
                builder.get();
            }
            try (okhttp3.Response r = client.newCall(builder.build()).execute()) {
                ResponseBody body = r.body();
                String bodyStr = body == null ? "" : body.string();
                if (bodyStr.contains("google.com/recaptcha")) {
                    throw new ReCaptchaException("reCaptcha challenge mila", request.url());
                }
                return new Response(r.code(), r.message(), r.headers().toMultimap(),
                        bodyStr, r.request().url().toString());
            }
        }
    }
}
