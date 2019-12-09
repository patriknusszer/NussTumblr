package com.Nusstudios.NussTumblr;

import com.Nusstudios.NussTumblr.com.Nusstudios.Core.DownloadCore;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import org.json.*;

public class MainController {
    public static Stage stage = new Stage();
    public ProgressBar progressbar;
    public TextField blogurl;
    public TextField outdir;
    public TextField cookie;
    public TextField useragent;
    public Label status;
    public Button btnPause;
    public Button btnCancel;
    public Button btnDownload;
    public RadioButton radiobutton_blog;
    public RadioButton radiobutton_post;
    public Button btnOutdir;
    public RadioButton fullmode;
    public RadioButton updtmode;
    public RadioButton checkmode;
    public static Task task;
    public static boolean isPause = false;
    public static boolean isCancel = false;
    public static String bloglink;

    public String rmTrailingSlash(String url) {
        if (url.charAt(url.length() - 1) == '/') {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }

    public void togglebbopts_ui() {
        fullmode.setDisable(false);
        updtmode.setDisable(false);
        checkmode.setDisable(false);
    }

    public void untogglebbopts_ui() {
        fullmode.setDisable(true);
        updtmode.setDisable(true);
        checkmode.setDisable(true);
    }

    public void dl_ui() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                btnPause.setDisable(false);
                btnCancel.setDisable(false);
                btnDownload.setDisable(true);
                outdir.setDisable(true);
                btnOutdir.setDisable(true);
                outdir.setDisable(true);
                btnOutdir.setDisable(true);
                blogurl.setDisable(true);
                radiobutton_blog.setDisable(true);
                radiobutton_post.setDisable(true);
                fullmode.setDisable(true);
                updtmode.setDisable(true);
                checkmode.setDisable(true);
            }
        });
    }

    public void dlFinished_ui() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                btnPause.setDisable(true);
                btnCancel.setDisable(true);
                btnDownload.setDisable(false);
                outdir.setDisable(false);
                btnOutdir.setDisable(false);
                outdir.setDisable(false);
                btnOutdir.setDisable(false);
                blogurl.setDisable(false);
                radiobutton_blog.setDisable(false);
                radiobutton_post.setDisable(false);

                if (radiobutton_blog.isSelected()) {
                    fullmode.setDisable(false);
                    updtmode.setDisable(false);
                    checkmode.setDisable(false);
                }
            }
        });
    }

    public void download() {
        File file_outdir = new File(outdir.getText());
        bloglink = rmTrailingSlash(blogurl.getText());

        if (file_outdir.exists()) {
            if (radiobutton_blog.isSelected())
            {
                if (!bloglink.isEmpty()) {
                    Pattern rgx = Pattern.compile("(https?:\\/\\/.*?\\.tumblr\\.com)");
                    Matcher mtchr = rgx.matcher(bloglink);

                    if (mtchr.find()) {
                        dl_ui();
                        int mode = 0;

                        if (updtmode.isSelected())
                        {
                        	mode = 1;
                        }
                        else if (checkmode.isSelected())
                        {
                        	mode = 2;
                        }

                        backupBlog(bloglink, outdir.getText(), 50, mode, cookie.getText(), useragent.getText());
                    }
                    else {
                        status.setText("Not a Tumblr blog URL");
                    }
                }
                else {
                    status.setText("Blog URL is not defined");
                }
            }
            else
            {
                Pattern rgx = Pattern.compile("(https?:\\/\\/.*?\\.tumblr\\.com)\\/post\\/(\\d*)");
                Matcher mtchr = rgx.matcher(bloglink);

                if (mtchr.find()) {
                    if (mtchr.groupCount() == 2) {
                        dl_ui();
                        backupPost(mtchr.group(1), Long.parseLong(mtchr.group(2)), cookie.getText(), useragent.getText());
                    }
                    else {
                        status.setText("Not a Tumblr post URL");
                    }
                }
            }
        } else {
            status.setText("Output directory does not exist");
        }
    }

    public void backupBlog(final String blogURL, final String outputDirectory, final int size, final int mode, String consentCookie, String userAgent)
    {
    	// 0 mode = full, 1 mode = update, 2 mode = check
        task = new Task<Void>() {
            @Override protected Void call() throws Exception{
                ByteArrayOutputStream bytearrayOStream = new ByteArrayOutputStream();
                byte[] byteArray;
                updateProgress(0, 1);
                updateMessage("Getting number of posts...");


                final JSONObject download_config = new JSONObject();
                final Object retrySleepLock = new Object();
                download_config.put("singleConnectionMaxErrorCount", 20);
                download_config.put("hashCheckFailureMaxErrorCount", 5);
                download_config.put("retryLaterMaxCount", 2);
                download_config.put("retryLaterCount", 0);
                final int singleConnectionMaxErrorCount = 20;
                final int hashCheckFailureMaxErrorCount = 5;
                final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                DownloadCore.download(blogURL + "/api/read?start=0&num=1", getHeaders(consentCookie, userAgent), 20000, 20000, (status) -> {
                    JSONObject response = null;

                    switch (status.getString("status")) {
                        case "exception":
                            response = new JSONObject();
                            JSONObject exception = status.getJSONObject("exception");

                            if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                    response.put("retry", false);
                                }
                                else {
                                    try {
                                        Runnable awakener = () -> {
                                            try {
                                                Thread.sleep(300000);

                                                    /* This thread will be able to acquire the lock
                                                    retrySleepLock and be able to enter the block despite
                                                    the fact that the parent thread already has it.
                                                    This is only possible in case wait() is called on this lock,
                                                    thus communicating nothing but that it is waiting for the next
                                                    thread to acquire the same lock and call notify() on the lock object */
                                                synchronized (retrySleepLock) {
                                                    retrySleepLock.notify();
                                                }
                                            }
                                            catch (Exception ex) { }
                                        };

                                        synchronized (retrySleepLock) {
                                            new Thread(awakener).start();
                                            retrySleepLock.wait();
                                        }

                                        response.put("retry", true);
                                    }
                                    catch (Exception ex) { }
                                }
                            }
                            else {
                                response.put("retry", true);
                            }

                            break;
                    }

                    return response;
                }, (chunk, progressTotal, clen) -> {
                    try {
                        buffer.write(chunk);
                    }
                    catch (Exception ex) {

                    }
                }, null, false);

                Document numPostDoc = Jsoup.parse(new String(buffer.toByteArray(), "UTF-8"));
                buffer.reset();
                Element postsTag = numPostDoc.getElementsByTag("posts").first();
                Long postNum = (Long.valueOf(postsTag.attr("total")) - 1);
                Long remainder = 0L;
                Long numSizePacks = 0L;
                updateProgress(0, 1);
                updateMessage("Downloading media...");
                String finalMessage = "Finished";

                if (size < postNum) {
                    remainder = postNum % size;
                    numSizePacks = postNum / size;
                    Long prog_post_num = 0L;
                    Boolean remainderTurn = false;

                    mainLoop:
                    for (int i = 1; i <= numSizePacks; i++) {
                        updateMessage("Downloading post pack...");
                        Document docSizePack;
                        String url;

                        if (!remainderTurn) {
                            url = bloglink + "/api/read?start=" + ((i - 1) * size) + "&num=" + size;
                        }
                        else {
                            url = bloglink + "/api/read?start=" + (numSizePacks * size) + "&num=" + remainder;
                        }

                        DownloadCore.download(url, getHeaders(consentCookie, userAgent), 20000, 20000, (status) -> {
                            JSONObject response = null;

                            switch (status.getString("status")) {
                                case "exception":
                                    response = new JSONObject();
                                    JSONObject exception = status.getJSONObject("exception");

                                    if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                        if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                            response.put("retry", false);
                                        }
                                        else {
                                            try {
                                                Runnable awakener = () -> {
                                                    try {
                                                        Thread.sleep(300000);

                                                    /* This thread will be able to acquire the lock
                                                    retrySleepLock and be able to enter the block despite
                                                    the fact that the parent thread already has it.
                                                    This is only possible in case wait() is called on this lock,
                                                    thus communicating nothing but that it is waiting for the next
                                                    thread to acquire the same lock and call notify() on the lock object */
                                                        synchronized (retrySleepLock) {
                                                            retrySleepLock.notify();
                                                        }
                                                    }
                                                    catch (Exception ex) { }
                                                };

                                                synchronized (retrySleepLock) {
                                                    new Thread(awakener).start();
                                                    retrySleepLock.wait();
                                                }

                                                response.put("retry", true);
                                            }
                                            catch (Exception ex) { }
                                        }
                                    }
                                    else {
                                        response.put("retry", true);
                                    }

                                    break;
                            }

                            return response;
                        }, (chunk, progressTotal, clen) -> {
                            try {
                                buffer.write(chunk);
                            }
                            catch (Exception ex) {

                            }
                        }, null, false);

                        docSizePack = Jsoup.parse(new String(buffer.toByteArray(), "UTF-8"));
                        buffer.reset();
                        Elements posts = docSizePack.getElementsByTag("post");

                        postLoop:
                        for (Element post: posts) {
                            if (isPause) {
                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        status.textProperty().bind(task.messageProperty());
                                    }
                                });

                                updateMessage("Paused");

                                while (true) {
                                    if (!isPause) {
                                        updateMessage("Downloading media...");
                                        break;
                                    }

                                    if (isCancel) {
                                        isCancel = false;

                                        Platform.runLater(new Runnable() {
                                            @Override
                                            public void run() {
                                                status.textProperty().bind(task.messageProperty());
                                            }
                                        });

                                        updateMessage("Cancelled");
                                        finalMessage = "Cancelled";
                                        break mainLoop;
                                    }

                                    Thread.sleep(1);
                                }
                            }

                            if (isCancel) {
                                isCancel = false;

                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        status.textProperty().bind(task.messageProperty());
                                    }
                                });

                                updateMessage("Cancelled");
                                finalMessage = "Cancelled";
                                break mainLoop;
                            }

                            prog_post_num++;
                            updateMessage("Processing post " + prog_post_num + " of " + postNum);
                            File postPath = new File(outputDirectory + File.separator + "Post_id_" + post.attr("id"));

                            if (postPath.exists())
                            {
                            	if (mode == 1)
                            	{
                                	break mainLoop;
                            	}
                            	else if (mode == 2)
                            	{
                            		continue postLoop;
                            	}
                            }

                            postPath.mkdir();
                            System.out.println(post.toString());

                            if (post.attr("type").equals("photo"))
                            {
                                Elements photos = post.getElementsByTag("photo-url");

                                for (Element photo: photos) {
                                    String strUrl = photo.html();
                                    System.out.println("photo:" + strUrl);
                                    File resPath = new File(postPath.getPath() + File.separator + photo.attr("max-width"));

                                    if (!resPath.exists()) {
                                        resPath.mkdir();
                                    }

                                    if (strUrl.substring(0, 2).equals("//")) {
                                        strUrl = "https:" + strUrl;
                                    }

                                    DownloadCore.downloadToFile(true, photo.html(), resPath.getPath() + File.separator + strUrl.substring(strUrl.lastIndexOf("/") + 1), null, 20000, 20000, (status) -> {
                                        JSONObject response = null;

                                        switch (status.getString("status")) {
                                            case "exception":
                                                response = new JSONObject();
                                                JSONObject exception = status.getJSONObject("exception");

                                                if (exception.getString("message").equals("RESPONSE_CODE_404")) {
                                                    resPath.delete();
                                                    response.put("retry", false);
                                                }
                                                else if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                                    if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                                        System.out.println("err: " + postPath.getPath() + " - " + photo.html());
                                                        response.put("retry", false);
                                                    }
                                                    else {
                                                        try {
                                                            Runnable awakener = () -> {
                                                                try {
                                                                    Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                                    synchronized (retrySleepLock) {
                                                                        retrySleepLock.notify();
                                                                    }
                                                                }
                                                                catch (Exception ex) { }
                                                            };

                                                            synchronized (retrySleepLock) {
                                                                new Thread(awakener).start();
                                                                retrySleepLock.wait();
                                                            }

                                                            response.put("retry", true);
                                                        }
                                                        catch (Exception ex) { }
                                                    }
                                                }
                                                else {
                                                    response.put("retry", true);
                                                }

                                                break;
                                            case "hashcheck_failure":
                                                response = new JSONObject();

                                                if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                                    response.put("retry", false);
                                                }
                                                else {
                                                    response.put("retry", false);
                                                }

                                                break;
                                        }

                                        return response;
                                    }, null, false);
                                }
                            }
                            else if (post.attr("type").equals("video"))
                            {
                                Elements videos = post.getElementsByTag("video-player");

                                for (Element video: videos)
                                {
                                    String videoHtml= video.html();
                                    Pattern srcPatten = Pattern.compile("src=\"(.*?)\"");
                                    Matcher srcMatcher = srcPatten.matcher(videoHtml);
                                    String videoUrl = "";

                                    if (srcMatcher.find())
                                    {
                                        videoUrl = srcMatcher.group(1);
                                    }

                                    if (!videoUrl.equals(""))
                                    {
                                        Pattern typePattern = Pattern.compile("type=\"(.*?)\"");
                                        Matcher typeMatcher = typePattern.matcher(videoHtml);
                                        String extension = "";

                                        if (typeMatcher.find())
                                        {
                                            extension = typeMatcher.group(1);
                                        }

                                        extension = "." + extension.substring(extension.indexOf("/") + 1);

                                        File resPath;

                                        if (!video.attr("max-width").equals(null))
                                        {
                                            resPath = new File(postPath.getPath() + File.separator + video.attr("max-width"));
                                        }
                                        else
                                        {
                                            resPath = new File(postPath.getPath());
                                        }


                                        if (!resPath.exists()) {
                                            resPath.mkdir();
                                        }

                                        System.out.println(videoUrl);

                                        if (videoUrl.substring(0, 2).equals("//")) {
                                            videoUrl = "https:" + videoUrl;
                                        }

                                        DownloadCore.downloadToFile(true, videoUrl, resPath.getPath() + File.separator + videoUrl.substring(videoUrl.lastIndexOf("/") + 1) + extension, null, 20000, 20000, (status) -> {
                                            JSONObject response = null;

                                            switch (status.getString("status")) {
                                                case "exception":
                                                    response = new JSONObject();
                                                    JSONObject exception = status.getJSONObject("exception");


                                                    if (exception.getString("message").equals("RESPONSE_CODE_404")) {
                                                        resPath.delete();
                                                        response.put("retry", false);
                                                    }
                                                    else if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                                        if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                                            System.out.println("err: " + postPath.getPath() + " - lasturl");
                                                            response.put("retry", false);
                                                        }
                                                        else {
                                                            try {
                                                                Runnable awakener = () -> {
                                                                    try {
                                                                        Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                                        synchronized (retrySleepLock) {
                                                                            retrySleepLock.notify();
                                                                        }
                                                                    }
                                                                    catch (Exception ex) { }
                                                                };

                                                                synchronized (retrySleepLock) {
                                                                    new Thread(awakener).start();
                                                                    retrySleepLock.wait();
                                                                }

                                                                response.put("retry", true);
                                                            }
                                                            catch (Exception ex) { }
                                                        }
                                                    }
                                                    else {
                                                        response.put("retry", true);
                                                    }

                                                    break;
                                                case "hashcheck_failure":
                                                    response = new JSONObject();

                                                    if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                                        response.put("retry", false);
                                                    }
                                                    else {
                                                        response.put("retry", false);
                                                    }

                                                    break;
                                            }

                                            return response;
                                        }, null, false);
                                    }
                                }
                            }
                            else if (post.attr("type").equals("audio"))
                            {
                                Element embedAudio = post.getElementsByTag("audio-embed").first();
                                String videoHtml= embedAudio.html();
                                Pattern srcPatten = Pattern.compile("src=\"(.*?)\"");
                                Matcher srcMatcher = srcPatten.matcher(videoHtml);
                                String audioUrl = "";

                                while (srcMatcher.find())
                                {
                                    audioUrl = srcMatcher.group(1);
                                }

                                String audioFileUrl = URLDecoder.decode(audioUrl.substring(audioUrl.indexOf("audio_file") /*the first one!*/ + "audio_file".length() + 1), "UTF-8");

                                if (audioUrl.substring(0, 2).equals("//")) {
                                    audioUrl = "https:" + audioUrl;
                                }

                                String signedAudioFileUrl = audioFileUrl + "?plead=please-dont-download-this-or-our-lawyers-wont-let-us-host-audio";

                                System.out.println(signedAudioFileUrl);

                                DownloadCore.downloadToFile(true, signedAudioFileUrl, postPath.getPath() + File.separator + audioFileUrl.substring(audioFileUrl.lastIndexOf("/") + 1) + ".mp3", null, 20000, 20000, (status) -> {
                                    JSONObject response = null;

                                    switch (status.getString("status")) {
                                        case "exception":
                                            response = new JSONObject();
                                            JSONObject exception = status.getJSONObject("exception");


                                            if (exception.getString("message").equals("RESPONSE_CODE_404")) {
                                                postPath.delete();
                                                response.put("retry", false);
                                            }
                                            else if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                                if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                                    System.out.println("err: " + postPath.getPath() + " - " + signedAudioFileUrl);
                                                    response.put("retry", false);
                                                }
                                                else {
                                                    try {
                                                        Runnable awakener = () -> {
                                                            try {
                                                                Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                                synchronized (retrySleepLock) {
                                                                    retrySleepLock.notify();
                                                                }
                                                            }
                                                            catch (Exception ex) { }
                                                        };

                                                        synchronized (retrySleepLock) {
                                                            new Thread(awakener).start();
                                                            retrySleepLock.wait();
                                                        }

                                                        response.put("retry", true);
                                                    }
                                                    catch (Exception ex) { }
                                                }
                                            }
                                            else {
                                                response.put("retry", true);
                                            }

                                            break;
                                        case "hashcheck_failure":
                                            response = new JSONObject();

                                            if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                                response.put("retry", false);
                                            }
                                            else {
                                                response.put("retry", false);
                                            }

                                            break;
                                    }

                                    return response;
                                }, null, false);
                            }

                            updateProgress(prog_post_num, postNum);
                        }

                        if (i == numSizePacks && !remainderTurn && remainder != 0) {
                            i--;
                            remainderTurn = true;
                        }
                    }
                }
                else {
                    remainder = postNum;
                    Long prog_post_num = 0L;

                    DownloadCore.download(blogURL + "/api/read?start=" + 0 + "&num=" + remainder, getHeaders(consentCookie, userAgent), 20000, 20000, (status) -> {
                        JSONObject response = null;

                        switch (status.getString("status")) {
                            case "exception":
                                response = new JSONObject();
                                JSONObject exception = status.getJSONObject("exception");

                                if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                    if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                        response.put("retry", false);
                                    }
                                    else {
                                        try {
                                            Runnable awakener = () -> {
                                                try {
                                                    Thread.sleep(300000);

                                                    /* This thread will be able to acquire the lock
                                                    retrySleepLock and be able to enter the block despite
                                                    the fact that the parent thread already has it.
                                                    This is only possible in case wait() is called on this lock,
                                                    thus communicating nothing but that it is waiting for the next
                                                    thread to acquire the same lock and call notify() on the lock object */
                                                    synchronized (retrySleepLock) {
                                                        retrySleepLock.notify();
                                                    }
                                                }
                                                catch (Exception ex) { }
                                            };

                                            synchronized (retrySleepLock) {
                                                new Thread(awakener).start();
                                                retrySleepLock.wait();
                                            }

                                            response.put("retry", true);
                                        }
                                        catch (Exception ex) { }
                                    }
                                }
                                else {
                                    response.put("retry", true);
                                }

                                break;
                        }

                        return response;
                    }, (chunk, progressTotal, clen) -> {
                        try {
                            buffer.write(chunk);
                        }
                        catch (Exception ex) {

                        }
                    }, null, false);

                    Document docSizePack = Jsoup.parse(new String(buffer.toByteArray(), "UTF-8"));
                    buffer.reset();
                    Elements posts = docSizePack.getElementsByTag("post");

                    mainLoop:
                    for (Element post: posts) {
                        if (isPause) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    status.textProperty().bind(task.messageProperty());
                                }
                            });

                            updateMessage("Paused");

                            while (true) {
                                if (!isPause) {
                                    updateMessage("Downloading media...");
                                    break;
                                }

                                if (isCancel) {
                                    isCancel = false;

                                    Platform.runLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            status.textProperty().bind(task.messageProperty());
                                        }
                                    });

                                    updateMessage("Cancelled");
                                    finalMessage = "Cancelled";
                                    break mainLoop;
                                }

                                Thread.sleep(1);
                            }
                        }

                        if (isCancel) {
                            isCancel = false;

                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    status.textProperty().bind(task.messageProperty());
                                }
                            });

                            updateMessage("Cancelled");
                            finalMessage = "Cancelled";
                            break mainLoop;
                        }

                        prog_post_num++;
                        File postPath = new File(outputDirectory + File.separator + "Post_id_" + post.attr("id"));
                        postPath.mkdir();

                        System.out.println(post.toString());

                        if (post.attr("type").equals("photo"))
                        {
                            Elements photos = post.getElementsByTag("photo-url");

                            for (Element photo: photos) {
                                String strUrl = photo.html();
                                System.out.println(strUrl);

                                if (strUrl.substring(0, 2).equals("//")) {
                                    strUrl = "https:" + strUrl;
                                }

                                File resPath = new File(postPath.getPath() + File.separator + photo.attr("max-width"));

                                if (!resPath.exists()) {
                                    resPath.mkdir();
                                }

                                DownloadCore.downloadToFile(true, photo.html(), resPath.getPath() + File.separator + strUrl.substring(strUrl.lastIndexOf("/") + 1), null, 20000, 20000, (status) -> {
                                    JSONObject response = null;

                                    switch (status.getString("status")) {
                                        case "exception":
                                            response = new JSONObject();
                                            JSONObject exception = status.getJSONObject("exception");

                                            if (exception.getString("message").equals("RESPONSE_CODE_404")) {
                                                resPath.delete();
                                                response.put("retry", false);
                                            }
                                            else if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                                if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                                    response.put("retry", false);
                                                    System.out.println("err: " + postPath.getPath() + " - " + photo.html());
                                                }
                                                else {
                                                    try {
                                                        Runnable awakener = () -> {
                                                            try {
                                                                Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                                synchronized (retrySleepLock) {
                                                                    retrySleepLock.notify();
                                                                }
                                                            }
                                                            catch (Exception ex) { }
                                                        };

                                                        synchronized (retrySleepLock) {
                                                            new Thread(awakener).start();
                                                            retrySleepLock.wait();
                                                        }

                                                        response.put("retry", true);
                                                    }
                                                    catch (Exception ex) { }
                                                }
                                            }
                                            else {
                                                response.put("retry", true);
                                            }

                                            break;
                                        case "hashcheck_failure":
                                            response = new JSONObject();

                                            if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                                response.put("retry", false);
                                            }
                                            else {
                                                response.put("retry", false);
                                            }

                                            break;
                                    }

                                    return response;
                                }, null, false);
                            }
                        }
                        else if (post.attr("type").equals("video"))
                        {
                            Elements videos = post.getElementsByTag("video-player");

                            for (Element video: videos)
                            {
                                String videoHtml= video.html();
                                Pattern srcPatten = Pattern.compile("src=\"(.*?)\"");
                                Matcher srcMatcher = srcPatten.matcher(videoHtml);
                                String videoUrl = "";

                                if (srcMatcher.find())
                                {
                                    videoUrl = srcMatcher.group(1);
                                }

                                if (!videoUrl.equals(""))
                                {
                                    Pattern typePattern = Pattern.compile("type=\"(.*?)\"");
                                    Matcher typeMatcher = typePattern.matcher(videoHtml);
                                    String extension = "";

                                    if (typeMatcher.find())
                                    {
                                        extension = typeMatcher.group(1);
                                    }

                                    extension = "." + extension.substring(extension.indexOf("/") + 1);

                                    File resPath;

                                    if (!video.attr("max-width").equals(null))
                                    {
                                        resPath = new File(postPath.getPath() + File.separator + video.attr("max-width"));
                                    }
                                    else
                                    {
                                        resPath = new File(postPath.getPath());
                                    }


                                    if (!resPath.exists()) {
                                        resPath.mkdir();
                                    }

                                    System.out.println(videoUrl);

                                    if (videoUrl.substring(0, 2).equals("//")) {
                                        videoUrl = "https:" + videoUrl;
                                    }

                                    DownloadCore.downloadToFile(true, videoUrl, resPath.getPath() + File.separator + videoUrl.substring(videoUrl.lastIndexOf("/") + 1) + extension, null, 20000, 20000, (status) -> {
                                        JSONObject response = null;

                                        switch (status.getString("status")) {
                                            case "exception":
                                                response = new JSONObject();
                                                JSONObject exception = status.getJSONObject("exception");

                                                if (exception.getString("message").equals("RESPONSE_CODE_404")) {
                                                    response.put("retry", false);
                                                    resPath.delete();
                                                }
                                                else if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                                    if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                                        response.put("retry", false);
                                                        System.out.println("err: " + postPath.getPath() + " - lasturl");
                                                    }
                                                    else {
                                                        try {
                                                            Runnable awakener = () -> {
                                                                try {
                                                                    Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                                    synchronized (retrySleepLock) {
                                                                        retrySleepLock.notify();
                                                                    }
                                                                }
                                                                catch (Exception ex) { }
                                                            };

                                                            synchronized (retrySleepLock) {
                                                                new Thread(awakener).start();
                                                                retrySleepLock.wait();
                                                            }

                                                            response.put("retry", true);
                                                        }
                                                        catch (Exception ex) { }
                                                    }
                                                }
                                                else {
                                                    response.put("retry", true);
                                                }

                                                break;
                                            case "hashcheck_failure":
                                                response = new JSONObject();

                                                if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                                    response.put("retry", false);
                                                }
                                                else {
                                                    response.put("retry", false);
                                                }

                                                break;
                                        }

                                        return response;
                                    }, null, false);
                                }
                            }
                        }
                        else if (post.attr("type").equals("audio"))
                        {
                            Element embedAudio = post.getElementsByTag("audio-embed").first();
                            String videoHtml= embedAudio.html();
                            Pattern srcPatten = Pattern.compile("src=\"(.*?)\"");
                            Matcher srcMatcher = srcPatten.matcher(videoHtml);
                            String audioUrl = "";

                            while (srcMatcher.find())
                            {
                                audioUrl = srcMatcher.group(1);
                            }

                            String audioFileUrl = URLDecoder.decode(audioUrl.substring(audioUrl.indexOf("audio_file") /*the first one!*/ + "audio_file".length() + 1), "UTF-8");

                            if (audioFileUrl.substring(0, 2).equals("//")) {
                                audioFileUrl = "https:" + audioFileUrl;
                            }

                            String signedAudioFileUrl = audioFileUrl + "?plead=please-dont-download-this-or-our-lawyers-wont-let-us-host-audio";
                            System.out.println(signedAudioFileUrl);

                            DownloadCore.downloadToFile(true, signedAudioFileUrl, postPath.getPath() + File.separator + audioFileUrl.substring(audioFileUrl.lastIndexOf("/") + 1) + ".mp3", null, 20000, 20000, (status) -> {
                                JSONObject response = null;

                                switch (status.getString("status")) {
                                    case "exception":
                                        response = new JSONObject();
                                        JSONObject exception = status.getJSONObject("exception");

                                        if (exception.getString("message").equals("RESPONSE_CODE_404")) {
                                            response.put("retry", false);
                                            postPath.delete();
                                        }
                                        else if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                            if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                                response.put("retry", false);
                                                System.out.println("err: " + postPath.getPath() + " - " + signedAudioFileUrl);
                                            }
                                            else {
                                                try {
                                                    Runnable awakener = () -> {
                                                        try {
                                                            Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                            synchronized (retrySleepLock) {
                                                                retrySleepLock.notify();
                                                            }
                                                        }
                                                        catch (Exception ex) { }
                                                    };

                                                    synchronized (retrySleepLock) {
                                                        new Thread(awakener).start();
                                                        retrySleepLock.wait();
                                                    }

                                                    response.put("retry", true);
                                                }
                                                catch (Exception ex) { }
                                            }
                                        }
                                        else {
                                            response.put("retry", true);
                                        }

                                        break;
                                    case "hashcheck_failure":
                                        response = new JSONObject();

                                        if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                            response.put("retry", false);
                                        }
                                        else {
                                            response.put("retry", false);
                                        }

                                        break;
                                }

                                return response;
                            }, null, false);
                        }

                        updateProgress(prog_post_num, postNum);
                        updateMessage("Processing post " + prog_post_num + " of " + postNum);
                    }
                }

                updateMessage(finalMessage);
                updateProgress(0, 1);
                dlFinished_ui();
                return null;
            }
        };

        progressbar.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        new Thread(task).start();
    }

    public HttpURLConnection createRequest(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9,hu;q=0.8");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");

        Pattern ptrn = Pattern.compile("https?:\\/\\/(www\\.)?([^\\/]*)\\/?.*");
        Matcher mtchr = ptrn.matcher(url);

        if (mtchr.find()) {
            String host = mtchr.group(2);
            connection.setRequestProperty("Host", host);
        }

        return connection;
    }

    public Map<String, String> getHeaders(String consentCookie, String userAgent)
    {
        Map<String, String> reqHeaders = new HashMap<>();
        reqHeaders.put("Cookie", consentCookie);
        reqHeaders.put("User-Agent", userAgent);
        reqHeaders.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        reqHeaders.put("Accept-Language", "en-US,en;q=0.9,hu;q=0.8");
        reqHeaders.put("Upgrade-Insecure-Requests", "1");
        return reqHeaders;
    }

    public void backupPost(final String blogUrl, final Long postId, String consentCookie, String userAgent)
    {
        task = new Task<Void>() {
            @Override protected Void call() throws Exception{
                final JSONObject download_config = new JSONObject();
                final Object retrySleepLock = new Object();
                download_config.put("singleConnectionMaxErrorCount", 20);
                download_config.put("hashCheckFailureMaxErrorCount", 5);
                download_config.put("retryLaterMaxCount", 2);
                download_config.put("retryLaterCount", 0);
                final int singleConnectionMaxErrorCount = 20;
                final int hashCheckFailureMaxErrorCount = 5;
                final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                DownloadCore.download(blogUrl + "/api/read?id=" + postId, getHeaders(consentCookie, userAgent), 20000, 20000, (status) -> {
                    JSONObject response = null;

                    switch (status.getString("status")) {
                        case "exception":
                            response = new JSONObject();
                            JSONObject exception = status.getJSONObject("exception");

                            if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                    response.put("retry", false);
                                }
                                else {
                                    try {
                                        Runnable awakener = () -> {
                                            try {
                                                Thread.sleep(300000);

                                                    /* This thread will be able to acquire the lock
                                                    retrySleepLock and be able to enter the block despite
                                                    the fact that the parent thread already has it.
                                                    This is only possible in case wait() is called on this lock,
                                                    thus communicating nothing but that it is waiting for the next
                                                    thread to acquire the same lock and call notify() on the lock object */
                                                synchronized (retrySleepLock) {
                                                    retrySleepLock.notify();
                                                }
                                            }
                                            catch (Exception ex) { }
                                        };

                                        synchronized (retrySleepLock) {
                                            new Thread(awakener).start();
                                            retrySleepLock.wait();
                                        }

                                        response.put("retry", true);
                                    }
                                    catch (Exception ex) { }
                                }
                            }
                            else {
                                response.put("retry", true);
                            }

                            break;
                    }

                    return response;
                }, (chunk, progressTotal, clen) -> {
                    try {
                        buffer.write(chunk);
                    }
                    catch (Exception ex) {

                    }
                }, null, false);

                String postHTMLStr = new String(buffer.toByteArray(), "UTF-8");
                buffer.reset();
                File postPath = new File(outdir.getText() + File.separator + "Post_id_" + postId);
                postPath.mkdir();
                FileOutputStream postConfig = new FileOutputStream(postPath.getPath() + File.separator + "config.html");
                postConfig.write(postHTMLStr.getBytes());
                postConfig.flush();
                postConfig.close();
                Document postDoc = Jsoup.parse(postHTMLStr);
                Element post = postDoc.getElementsByTag("post").first();

                updateMessage("Downloading media...");

                if (post.attr("type").equals("photo"))
                {
                    Elements photos = post.getElementsByTag("photo-url");

                    for (Element photo: photos) {
                        String strUrl = photo.html();
                        System.out.println(strUrl);
                        File resPath = new File(postPath.getPath() + File.separator + photo.attr("max-width"));

                        if (!resPath.exists()) {
                            resPath.mkdir();
                        }

                        DownloadCore.downloadToFile(true, photo.html(), resPath.getPath() + File.separator + strUrl.substring(strUrl.lastIndexOf("/") + 1), null, 20000, 20000, (status) -> {
                            JSONObject response = null;

                            switch (status.getString("status")) {
                                case "exception":
                                    response = new JSONObject();
                                    JSONObject exception = status.getJSONObject("exception");

                                    if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                        if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                            response.put("retry", false);
                                        }
                                        else {
                                            try {
                                                Runnable awakener = () -> {
                                                    try {
                                                        Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                        synchronized (retrySleepLock) {
                                                            retrySleepLock.notify();
                                                        }
                                                    }
                                                    catch (Exception ex) { }
                                                };

                                                synchronized (retrySleepLock) {
                                                    new Thread(awakener).start();
                                                    retrySleepLock.wait();
                                                }

                                                response.put("retry", true);
                                            }
                                            catch (Exception ex) { }
                                        }
                                    }
                                    else {
                                        response.put("retry", true);
                                    }

                                    break;
                                case "hashcheck_failure":
                                    response = new JSONObject();

                                    if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                        response.put("retry", false);
                                    }
                                    else {
                                        response.put("retry", false);
                                    }

                                    break;
                            }

                            return response;
                        }, null, false);
                    }
                }
                else if (post.attr("type").equals("video"))
                {
                    Elements videos = post.getElementsByTag("video-player");

                    for (Element video: videos)
                    {
                        String videoHtml= video.html();
                        Pattern srcPatten = Pattern.compile("src=\"(.*?)\"");
                        Matcher srcMatcher = srcPatten.matcher(videoHtml);
                        String videoUrl = "";

                        if (srcMatcher.find())
                        {
                            videoUrl = srcMatcher.group(1);
                        }

                        if (!videoUrl.equals(""))
                        {
                            Pattern typePattern = Pattern.compile("type=\"(.*?)\"");
                            Matcher typeMatcher = typePattern.matcher(videoHtml);
                            String extension = "";

                            if (typeMatcher.find())
                            {
                                extension = typeMatcher.group(1);
                            }

                            extension = "." + extension.substring(extension.indexOf("/") + 1);

                            File resPath;

                            if (!video.attr("max-width").equals(null))
                            {
                                resPath = new File(postPath.getPath() + File.separator + video.attr("max-width"));
                            }
                            else
                            {
                                resPath = new File(postPath.getPath());
                            }


                            if (!resPath.exists()) {
                                resPath.mkdir();
                            }

                            System.out.println(videoUrl);

                            DownloadCore.downloadToFile(true, videoUrl, resPath.getPath() + File.separator + videoUrl.substring(videoUrl.lastIndexOf("/") + 1) + extension, null, 20000, 20000, (status) -> {
                                JSONObject response = null;

                                switch (status.getString("status")) {
                                    case "exception":
                                        response = new JSONObject();
                                        JSONObject exception = status.getJSONObject("exception");

                                        if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                            if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                                response.put("retry", false);
                                            }
                                            else {
                                                try {
                                                    Runnable awakener = () -> {
                                                        try {
                                                            Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                            synchronized (retrySleepLock) {
                                                                retrySleepLock.notify();
                                                            }
                                                        }
                                                        catch (Exception ex) { }
                                                    };

                                                    synchronized (retrySleepLock) {
                                                        new Thread(awakener).start();
                                                        retrySleepLock.wait();
                                                    }

                                                    response.put("retry", true);
                                                }
                                                catch (Exception ex) { }
                                            }
                                        }
                                        else {
                                            response.put("retry", true);
                                        }

                                        break;
                                    case "hashcheck_failure":
                                        response = new JSONObject();

                                        if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                            response.put("retry", false);
                                        }
                                        else {
                                            response.put("retry", false);
                                        }

                                        break;
                                }

                                return response;
                            }, null, false);
                        }
                    }
                }
                else if (post.attr("type").equals("audio"))
                {
                    Element embedAudio = post.getElementsByTag("audio-embed").first();
                    String videoHtml= embedAudio.html();
                    Pattern srcPatten = Pattern.compile("src=\"(.*?)\"");
                    Matcher srcMatcher = srcPatten.matcher(videoHtml);
                    String audioUrl = "";

                    while (srcMatcher.find())
                    {
                        audioUrl = srcMatcher.group(1);
                    }

                    String audioFileUrl = URLDecoder.decode(audioUrl.substring(audioUrl.indexOf("audio_file") /*the first one!*/ + "audio_file".length() + 1), "UTF-8");
                    String signedAudioFileUrl = audioFileUrl + "?plead=please-dont-download-this-or-our-lawyers-wont-let-us-host-audio";

                    System.out.println(signedAudioFileUrl);

                    DownloadCore.downloadToFile(true, signedAudioFileUrl, postPath.getPath() + File.separator + audioFileUrl.substring(audioFileUrl.lastIndexOf("/") + 1) + ".mp3", null, 20000, 20000, (status) -> {
                        JSONObject response = null;

                        switch (status.getString("status")) {
                            case "exception":
                                response = new JSONObject();
                                JSONObject exception = status.getJSONObject("exception");

                                if (exception.getInt("error_count") >= singleConnectionMaxErrorCount) {
                                    if (download_config.getInt("retryLaterCount") == download_config.getInt("retryLaterMaxCount")) {
                                        response.put("retry", false);
                                    }
                                    else {
                                        try {
                                            Runnable awakener = () -> {
                                                try {
                                                    Thread.sleep(300000);

                                                                    /* This thread will be able to acquire the lock
                                                                    retrySleepLock and be able to enter the block despite
                                                                    the fact that the parent thread already has it.
                                                                    This is only possible in case wait() is called on this lock,
                                                                    thus communicating nothing but that it is waiting for the next
                                                                    thread to acquire the same lock and call notify() on the lock object */
                                                    synchronized (retrySleepLock) {
                                                        retrySleepLock.notify();
                                                    }
                                                }
                                                catch (Exception ex) { }
                                            };

                                            synchronized (retrySleepLock) {
                                                new Thread(awakener).start();
                                                retrySleepLock.wait();
                                            }

                                            response.put("retry", true);
                                        }
                                        catch (Exception ex) { }
                                    }
                                }
                                else {
                                    response.put("retry", true);
                                }

                                break;
                            case "hashcheck_failure":
                                response = new JSONObject();

                                if (status.getJSONObject("hashcheck_failure").getInt("error_count") >= hashCheckFailureMaxErrorCount) {
                                    response.put("retry", false);
                                }
                                else {
                                    response.put("retry", false);
                                }

                                break;
                        }

                        return response;
                    }, null, false);
                }

                updateProgress(0, 1);
                updateMessage("Finished");
                dlFinished_ui();
                return null;
            }
        };

        progressbar.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        new Thread(task).start();
    }

    public void pause() {
        if (!isPause) {
            status.textProperty().unbind();
            status.setText("Finishing current post...");
            btnPause.setText("Resume");

            isPause = true;
        }
        else {
            btnPause.setText("Pause");
            isPause = false;
        }
    }

    public void cancel() {
        status.textProperty().unbind();
        status.setText("Finishing current post...");
        isCancel = true;
    }

    public void opendir() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Save directory");
        File f = directoryChooser.showDialog(stage);

        if (f != null)
        {
            outdir.setText(f.getPath());
        }
    }
}
