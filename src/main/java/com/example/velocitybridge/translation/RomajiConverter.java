package com.example.velocitybridge.translation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Google Input Tools API を使ってローマ字 → 日本語変換を行うクラス。
 *
 * レスポンス形式:
 *   ["SUCCESS", [["input", ["変換候補1", "変換候補2", ...], {metadata}]]]
 *
 * 非公式 API のため予告なく変更される可能性があります。
 */
public class RomajiConverter {

    private static final String API_ENDPOINT = "https://inputtools.google.com/request";
    private final HttpClient httpClient;

    public RomajiConverter() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    /**
     * ローマ字テキストを日本語に変換して返す。
     * 変換失敗・エラー時は元テキストをそのまま返す。
     *
     * @param romaji 変換前のテキスト（ローマ字）
     * @return 変換後のテキスト（失敗時は romaji をそのまま返す）
     */
    public String convert(String romaji) {
        if (romaji == null || romaji.isBlank()) return romaji;
        try {
            String encoded = URLEncoder.encode(romaji, StandardCharsets.UTF_8);
            String url = API_ENDPOINT
                + "?text=" + encoded
                + "&itc=ja-t-i0-und"   // 日本語変換
                + "&num=1"              // 候補1件
                + "&ie=utf-8"
                + "&oe=utf-8";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "Mozilla/5.0")  // 一部環境で必要
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String result = parseResponse(response.body());
                return result != null ? result : romaji;
            }
        } catch (IOException e) {
            // ネットワークエラーは無視して元テキストを返す
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return romaji;
    }

    /**
     * Gson を使って API レスポンスをパースし、最初の変換候補を返す。
     *
     * レスポンス構造:
     *   index 0: "SUCCESS"
     *   index 1: [ [入力文字列, [候補1, 候補2, ...], {metadata}] ]
     *
     * @return 変換候補の先頭、または取得できない場合は null
     */
    private String parseResponse(String body) {
        try {
            JsonArray root = JsonParser.parseString(body).getAsJsonArray();

            // root[0] が "SUCCESS" でなければ失敗
            if (!root.get(0).getAsString().equals("SUCCESS")) return null;

            // root[1] → セグメント配列
            JsonArray segments = root.get(1).getAsJsonArray();
            if (segments.isEmpty()) return null;

            // segments[0] = ["入力", ["候補1", "候補2", ...], {...}]
            JsonArray firstSegment = segments.get(0).getAsJsonArray();
            if (firstSegment.size() < 2) return null;

            // firstSegment[1] = 変換候補配列
            JsonArray candidates = firstSegment.get(1).getAsJsonArray();
            if (candidates.isEmpty()) return null;

            // 先頭候補を返す
            JsonElement best = candidates.get(0);
            return best.getAsString();

        } catch (Exception e) {
            return null;
        }
    }
}
