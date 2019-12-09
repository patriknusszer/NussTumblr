package com.Nusstudios.NussTumblr.com.Nusstudios.Core;

import org.json.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

public class DownloadCore {
    public interface CheckSignal {
        JSONObject checkSignal();
    }

    public interface DownloadCallback {
        JSONObject stateChanged(JSONObject state);
    }

    public interface ChunkCallback {
        void chunkReceived(byte[] chunk, long progressTotal, long clen);
    }

    public static void download(boolean doubleCheck, String url, Map<String, String> requestHeaders,  int timeout, int readTimeout, DownloadCallback cb, ChunkCallback optionalCCB, CheckSignal chk, boolean async) {
        if (doubleCheck) {
            Runnable dlVar = () -> {
                int errorCount = 0;

                do {
                    try {
                        int runCount = 2;
                        byte[][] hashes = new byte[16][runCount];

                        for (int i = 1; i <= runCount; i++) {
                        	final int _i = i;
                            final MessageDigest md = MessageDigest.getInstance("MD5");
                            tryReportStatus(cb, createRunStartedStatus(i));
                            final JSONObject report = new JSONObject();

                            downloadSync(
                                url,
                                requestHeaders,
                                timeout,
                                readTimeout,
                                (status) -> {
                                	status.put("run_index", _i - 1);
                                    JSONObject response = tryReportStatus(cb, status);

                                    switch (status.getString("status")) {
                                        case "progress":
                                            byte[] chunk = Base64.getDecoder().decode(status.getJSONObject("progress").getString("chunk"));
                                            md.update(chunk);
                                            break;
                                        case "exception":
                                            if (!responseTryCheckRetry(response)) {
                                                report.put("result", "exception");
                                            }

                                            break;
                                        case "cancel":
                                            report.put("result", "cancel");
                                            break;
                                        case "success":
                                            report.put("result", "success");
                                            break;
                                    }

                                    return response;
                                },
                                (chunk, progressTotal, clen) -> {
                                    optionalCCB.chunkReceived(chunk, progressTotal, clen);
                                    md.update(chunk);
                                },
                                chk
                            );

                            String result = report.getString("result");

                            if (result.equals("cancel") || result.equals("exception")) {
                                return;
                            }
                        }

                        tryReportStatus(cb, createHashingStatus());

                        try {
                            if (hashCheck(hashes)) {
                                tryReportStatus(cb, createHashCheckSuccessStatus());
                                return;
                            }
                            else {
                                errorCount++;

                                if (!responseTryCheckRetry(tryReportStatus(cb, createHashCheckFailureStatus(errorCount)))) {
                                    return;
                                }
                            }
                        }
                        catch (Exception ex) {

                        }
                    }
                    catch (Exception ex) {

                    }
                } while(true);
            };

            if (async) {
                new Thread(dlVar).start();
            }
            else {
                dlVar.run();
            }
        }
        else {
            downloadSync(url, requestHeaders, timeout, readTimeout, cb, optionalCCB, chk);
        }
    }

    public static void downloadToFile(boolean doubleCheck, String url, String fn, Map<String, String> requestHeaders, int timeout, int readTimeout, DownloadCallback cb, CheckSignal chk, boolean async) {
        if (doubleCheck) {
            Runnable dltfVar = () -> {
                int errorCount = 0;

                do {
                    int runCount = 2;
                    List<String> fileNames = new ArrayList<>();

                    for (int i = 1; i <= runCount; i++) {
                        final int _i = i;
                        tryReportStatus(cb, createRunStartedStatus(i));
                        String runFn = fn + "." + i;
                        fileNames.add(runFn);
                        final JSONObject report = new JSONObject();

                        downloadToFileSync(
                            url,
                            runFn,
                            requestHeaders,
                            timeout,
                            readTimeout,
                            (status) -> {
                                status.put("run_index", _i - 1);
                                JSONObject response = tryReportStatus(cb, status);

                                switch (status.getString("status")) {
                                    case "exception":
                                        report.put("result", "exception");
                                        break;
                                    case "cancel":
                                        report.put("result", "cancel");
                                        break;
                                    case "success":
                                        report.put("result", "success");
                                        break;
                                }

                                return response;
                            },
                            chk
                        );

                        String result = report.getString("result");

                        if (result.equals("cancel") || result.equals("exception")) {
                            return;
                        }
                    }

                    tryReportStatus(cb, createHashingStatus());

                    try {
                        if (hashCheckFiles(fileNames)) {
                            for (int i = 1; i < fileNames.size(); i++) {
                                new File(fileNames.get(i)).delete();
                            }

                            new File(fileNames.get(0)).renameTo(new File(fn));
                            tryReportStatus(cb, createHashCheckSuccessStatus());
                            return;
                        }
                        else {
                            errorCount++;

                            for (int i = 0; i < fileNames.size(); i++) {
                                new File(fileNames.get(i)).delete();
                            }

                            if (!responseTryCheckRetry(tryReportStatus(cb, createHashCheckFailureStatus(errorCount)))) {
                                return;
                            }
                        }
                    }
                    catch (Exception ex) {

                    }
                } while(true);
            };

            if (async) {
                new Thread(dltfVar).start();
            }
            else {
                dltfVar.run();
            }
        }
        else {
            downloadToFile(url, fn, requestHeaders, timeout, readTimeout, cb, chk, async);
        }
    }

    private static boolean hashCheckFiles(List<String> fileNames) throws Exception {
        byte[][] hashes = new byte[fileNames.size()][16];

        for (int i = 0; i < fileNames.size(); i++) {
            DigestInputStream dis = new DigestInputStream(new FileInputStream(fileNames.get(i)), MessageDigest.getInstance("MD5"));
            byte[] byteArray = new byte[26214400];
            while (dis.read(byteArray) != - 1);
            hashes[i] = dis.getMessageDigest().digest();
        }

        return hashCheck(hashes);
    }

    private static boolean hashCheck(byte[][] hashes) {
        if (hashes.length > 1) {
            byte[] hash = hashes[0];

            for (int i = 1; i < hashes.length; i++) {
                if (!Arrays.equals(hash, hashes[i])) {
                    return false;
                }
            }
        }

        return true;
    }

    public static void downloadToFile(String url, String fn, Map<String, String> requestHeaders, int timeout, int readTimeout, DownloadCallback cb, CheckSignal chk, boolean async) {
        if (async) {
            new Thread(() -> {
                downloadToFileSync(url, fn, requestHeaders, timeout, readTimeout, cb, chk);
            }).start();
        }
        else {
            downloadToFileSync(url, fn, requestHeaders, timeout, readTimeout, cb, chk);
        }
    }

    // If ChunkCallback is null, the method will send chunks through DownloadCallback as Base64 strings in JSON
    public static void download(String url, Map<String, String> requestHeaders,  int timeout, int readTimeout, DownloadCallback cb, ChunkCallback optionalCCB, CheckSignal chk, boolean async) {
        if (async) {
            new Thread(() -> {
                downloadSync(url, requestHeaders,  timeout, readTimeout, cb, optionalCCB, chk);
            }).start();
        }
        else {
            downloadSync(url, requestHeaders,  timeout, readTimeout, cb, optionalCCB, chk);
        }
    }

    private static JSONObject tryReportStatus(DownloadCallback cb, JSONObject status) {
        if (cb != null) {
            return cb.stateChanged(status);
        }
        else {
            return null;
        }
    }

    private static JSONObject createHashCheckSuccessStatus() {
        JSONObject status = new JSONObject();
        status.put("status", "hashcheck_success");
        return status;
    }

    private static JSONObject createHashCheckFailureStatus(int errorCount) {
        JSONObject status = new JSONObject();
        status.put("status", "hashcheck_failure");
        JSONObject hashcheck_failure = new JSONObject();
        hashcheck_failure.put("error_count", errorCount);
        status.put("hashcheck_failure", hashcheck_failure);
        return status;
    }

    private static JSONObject createHashingStatus() {
        JSONObject status = new JSONObject();
        status.put("status", "hashing");
        return status;
    }

    private static JSONObject createRunStartedStatus(int nth_run) {
        JSONObject status = new JSONObject();
        status.put("status", "run_started");
        status.put("run_started", nth_run);
        return status;
    }

    private static JSONObject createExceptionStatus(String message, int errorCount) {
        JSONObject status = new JSONObject();
        status.put("status", "exception");
        JSONObject exception = new JSONObject();
        exception.put("message", message);
        exception.put("error_count", errorCount);
        status.put("exception", exception);
        return status;
    }

    private static JSONObject createConnectingStatus() {
        JSONObject status = new JSONObject();
        status.put("status", "connecting");
        return status;
    }

    private static JSONObject createProgressStatus(long progressTotal, long clen) {
        JSONObject status = new JSONObject();
        status.put("status", "progress");
        JSONArray progress = new JSONArray();
        progress.put(progressTotal);
        progress.put(clen);
        status.put("progress", progress);
        return status;
    }

    private static JSONObject createProgressStatus(byte[] chunk, long progressTotal, long clen) {
        JSONObject status = new JSONObject();
        status.put("status", "progress");
        JSONObject progress = new JSONObject();
        progress.put("chunk", Base64.getEncoder().encode(chunk));
        JSONArray numbers = new JSONArray();
        numbers.put(progressTotal);
        numbers.put(clen);
        progress.put("numbers", numbers);
        status.put("progress", numbers);
        return status;
    }

    private static JSONObject createConnectedStatus(int responseCode) {
        JSONObject status = new JSONObject();
        status.put("status", "connected");
        status.put("connected", responseCode);
        return status;
    }

    private static JSONObject createCancelStatus() {
        JSONObject status = new JSONObject();
        status.put("status", "cancel");
        return status;
    }

    private static JSONObject createSuccessStatus() {
        JSONObject status = new JSONObject();
        status.put("status", "success");
        return status;
    }

    private static boolean responseTryCheckDeleteFile(JSONObject response) {
        if (response != null) {
            if (response.has("delete_file")) {
                return response.getBoolean("delete_file");
            }
            else {
                return true;
            }
        }
        else {
            return true;
        }
    }

    private static boolean responseTryCheckResetErrorCount(JSONObject response) {
        if (response.has("reset_error_count")) {
            return response.getBoolean("reset_error_count");
        }
        else {
            return false;
        }
    }

    private static boolean responseTryCheckRetry(JSONObject response) {
        if (response.has("retry")) {
            return response.getBoolean("retry");
        }
        else {
            return false;
        }
    }

    private static boolean signalTryCheckCancel(JSONObject signal) {
        if (signal != null) {
            if (signal.has("request")) {
                if (signal.getString("request").equals("cancel")) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void downloadToFileSync(String url, String fn, Map<String, String> requestHeaders, int timeout, int readTimeout, DownloadCallback cb, CheckSignal chk) {
        int errorCount = 0;

        do {
            try {
                byte[] byteArray = new byte[26214400];
                HttpURLConnection streamConnection;
                int code = 0;

                do {
                    tryReportStatus(cb, createConnectingStatus());
                    streamConnection = (HttpURLConnection)new URL(url).openConnection();

                    if (requestHeaders != null) {
                        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                            streamConnection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }

                    streamConnection.setConnectTimeout(timeout);
                    streamConnection.setReadTimeout(readTimeout);
                    streamConnection.connect();
                    code = streamConnection.getResponseCode();
                    tryReportStatus(cb, createConnectedStatus(code));

                    if (code != 200) {
                        errorCount++;
                        JSONObject response = tryReportStatus(cb, createExceptionStatus("RESPONSE_CODE_" + code, errorCount));

                        if (!responseTryCheckRetry(response)) {
                            return;
                        }
                        else if (responseTryCheckResetErrorCount(response)) {
                            errorCount = 0;
                        }
                    }
                    else {
                        break;
                    }
                } while (true);

                Long clen = -1L;
                String clenStr = streamConnection.getHeaderField("Content-Length");

                if (clenStr != null) {
                    clen = Long.valueOf(clenStr);
                }

                InputStream iStream = streamConnection.getInputStream();
                int progress = 0;
                int progressTotal = 0;
                FileOutputStream fs = new FileOutputStream(fn);

                while((progress = iStream.read(byteArray)) != -1) {
                    progressTotal += progress;
                    byte[] realBuffer = Arrays.copyOfRange(byteArray, 0, progress);
                    fs.write(realBuffer);
                    tryReportStatus(cb, createProgressStatus(progressTotal, clen));

                    if (chk != null) {
                        JSONObject signal = chk.checkSignal();

                        if (signalTryCheckCancel(signal)) {
                            iStream.close();
                            streamConnection.disconnect();
                            fs.flush();
                            fs.close();

                            if (responseTryCheckDeleteFile(tryReportStatus(cb, createCancelStatus()))) {
                                new File(fn).delete();
                            }

                            return;
                        }
                    }
                }

                iStream.close();
                streamConnection.disconnect();
                fs.flush();
                fs.close();
                tryReportStatus(cb, createSuccessStatus());
                return;
            }
            catch (Exception ex) {
                errorCount++;
                JSONObject response = tryReportStatus(cb, createExceptionStatus(ex.getMessage(), errorCount));

                if (!responseTryCheckRetry(response)) {
                    return;
                }
                else if (responseTryCheckResetErrorCount(response)) {
                    errorCount = 0;
                }
            }
        } while (true);
    }

    private static void downloadSync(String url, Map<String, String> requestHeaders,  int timeout, int readTimeout, DownloadCallback cb, ChunkCallback optionalCCB, CheckSignal chk) {
        int errorCount = 0;

        do {
            try {
                byte[] byteArray = new byte[26214400];
                HttpURLConnection streamConnection;
                int code = 0;

                do {
                    tryReportStatus(cb, createConnectingStatus());
                    streamConnection = (HttpURLConnection)new URL(url).openConnection();

                    if (requestHeaders != null) {
                        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                            streamConnection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }

                    streamConnection.setConnectTimeout(timeout);
                    streamConnection.setReadTimeout(readTimeout);
                    streamConnection.connect();
                    code = streamConnection.getResponseCode();
                    tryReportStatus(cb, createConnectedStatus(code));

                    if (code != 200) {
                        errorCount++;
                        JSONObject response = tryReportStatus(cb, createExceptionStatus("RESPONSE_CODE_" + code, errorCount));

                        if (!responseTryCheckRetry(response)) {
                            return;
                        }
                        else if (responseTryCheckResetErrorCount(response)) {
                            errorCount = 0;
                        }
                    }
                    else {
                        break;
                    }
                } while (true);

                Long clen = -1L;
                String clenStr = streamConnection.getHeaderField("Content-Length");

                if (clenStr != null) {
                    clen = Long.valueOf(clenStr);
                }

                InputStream iStream = streamConnection.getInputStream();
                int progress = 0;
                int progressTotal = 0;

                while((progress = iStream.read(byteArray)) != -1) {
                    progressTotal += progress;
                    byte[] realBuffer = Arrays.copyOfRange(byteArray, 0, progress);

                    if (optionalCCB == null) {
                        tryReportStatus(cb, createProgressStatus(realBuffer, progressTotal, clen));
                    }
                    else {
                        optionalCCB.chunkReceived(realBuffer, progressTotal, clen);
                    }

                    if (chk != null) {
                        JSONObject signal = chk.checkSignal();

                        if (signalTryCheckCancel(signal)) {
                            iStream.close();
                            streamConnection.disconnect();
                            tryReportStatus(cb, createCancelStatus());
                            return;
                        }
                    }
                }

                iStream.close();
                streamConnection.disconnect();
                tryReportStatus(cb, createSuccessStatus());
                return;
            }
            catch (Exception ex) {
                errorCount++;
                JSONObject response = tryReportStatus(cb, createExceptionStatus(ex.getMessage(), errorCount));

                if (!responseTryCheckRetry(response)) {
                    return;
                }
                else if (responseTryCheckResetErrorCount(response)) {
                    errorCount = 0;
                }
            }
        } while (true);
    }
}
