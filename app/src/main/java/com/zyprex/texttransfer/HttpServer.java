package com.zyprex.texttransfer;

import android.os.Environment;
//import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
//    private static final String TAG = "HttpServer";
    private String savedText = "";
    private final String textFormHTML = MainActivity.getTextFormHTML();
    private final String fileListHTML = MainActivity.getFileListHTML();
    private final String uploadFormHTML= MainActivity.getUploadFormHTML();

    public HttpServer(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (session.getMethod() == Method.GET) {
//            Log.d(TAG, "serve: GET " + uri);
            Map<String, String> params = session.getParms();
            // request with ?text=some_text
            String textReqParameter = params.get("text");
            if (textReqParameter != null) {
                savedText = textReqParameter;
            }
            // return raw text
            if (uri.matches("/r/?")) {
                return newFixedLengthResponse(savedText);
            }
            // return file list /f?p=/sdcard
            if (uri.matches("/f/?")) {
                String path = params.get("p");
                if (path != null) {
                    return fileListResponseResult(path);
                }
                String download = params.get("download");
                if (download != null) {
                    return fileListDownload(download);
                }
                return msgReturn(Response.Status.NOT_FOUND,"<h1>Error Path</h1>");
            }
            // upload file and download it to server (by copy temp file)
            if (uri.matches("/u/?")) {
                return uploadFormResponseResult();
            }
            return textFormResponseResult(savedText);
        } else if (session.getMethod() == Method.POST) {
//            Log.d(TAG, "serve: POST " + uri);
            if (uri.matches("/")) {
                try {
                    session.parseBody(new HashMap<>());
                    return postSubmitText(session);
                } catch (IOException | ResponseException e) {
                    e.printStackTrace();
                }
            }
            if (uri.matches("/upload/?")) {
                try {
                    Map<String, String> files = new HashMap<>();
                    session.parseBody(files);
                    return postUploadFile(session, files);
                } catch (IOException | ResponseException e) {
                    e.printStackTrace();
                }
            }
        }
        return msgReturn(Response.Status.NOT_FOUND, "<h1>Incorrect Request</h1>");
    }

    private String mimeTypeDetect(String path) {
        String mt = "application/octet-stream";
        String[] mimeTypes = {
                "(txt|md|rst|org|conf|in|log)", "text/plain",
                "xml", "text/xml",
                "opml", "text/x-opml",
                "(htm|html)", "text/html",
                "css", "text/css",
                "java", "text/x-java-source",
                "(c|cpp|cxx)", "text/x-c",
                "js", "application/javascript",
                "csv", "text/csv",
                "gif", "image/gif",
                "png", "image/png",
                "(jpg|jpeg)", "image/jpeg",
                "bmp", "image/bmp",
                "svg", "image/svg+xml",
                "webp", "image/webp",
                "(mid|midi)", "audio/midi",
                "wav", "audio/wav",
                "webm", "audio/webm",
                "(mpg|mp3)", "audio/mpeg",
                "mp4", "video/mp4",
                "flv", "video/x-flv",
                "ogg", "application/x-ogg",
                "pdf", "application/pdf",
                "zip", "application/zip",
                "7z", "application/x-7z-compressed",
                "atom", "application/atom+xml",
                "chm", "application/vnd.ms-htmlhelp",
                "(xhtm|xhtml)", "application/html+xml",
                "class", "application/java",
                "jar", "application/java-archive",
                "epub", "application/epub+zip",
                "mobi", "application/x-mobipocket-ebook",
                "latex", "application/x-latex",

        };
        for (int i = 0; i < mimeTypes.length; i = i + 2) {
            if (path.matches(".*\\." + mimeTypes[i] + "$")) {
                mt = mimeTypes[i + 1 ];
                break;
            }
        }
        // Log.d(TAG, "mimeTypeDetect: " + mt);
        return mt;
    }

    private Response msgReturn(Response.Status status, String body) {
        return newFixedLengthResponse(status, MIME_HTML,
                "<!DOCTYPE html><style>*{margin:0;padding:0;text-decoration:none;}</style>"  +
                        "<html><head><title>TextTransfer Web Client</title>" +
                        "<meta http-equiv='X-UA-Compatible' content='IE=edge' />" +
                        "<meta charset='utf-8' />" +
                        "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                        "</head><body>" + body + "</body></html>");
    }

    private Response textFormResponseResult(String savedText) {
        return newFixedLengthResponse(textFormHTML
                .replaceAll("RX_LINKS", makeLinks(extractUrl(savedText)))
                .replaceAll("RX_TEXT", savedText)
                .replaceAll("RX_ACCESS_PATH", MainActivity.getAccessPath())
        );
    }

    private String fileSizeHumanReadableFormat(long fileLen) {
        if (fileLen < 1024) {
            return String.format(Locale.getDefault(), "%d B", fileLen);
        } else if (fileLen < 1024*1024) {
            return String.format(Locale.getDefault(), "%.1f KiB", fileLen / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.1f MiB", fileLen / 1024.0 / 1024);
        }
    }

    private String fileLastModHumanReadableFormat(long lastMod) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd E HH:mm:ss", Locale.getDefault());
        return sdf.format(lastMod);
    }

    private String fileListHTMLReturn(String path) {
        List<String> filePaths = new ArrayList<>();
        File currentPath = new File(path);
        if (!currentPath.exists()) {
            return "<h1>Error Path</h1>";
        }
        // download page
        if (!currentPath.isDirectory()) {
//            DateFormat df = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault());
            return "<a href=\"/\">[HOME]</a>" +
                    "<h3><a href=\"/f?p=" + currentPath.getParent() + "\">" + currentPath + "</a></h3>" +
                    "<p>Size: " + fileSizeHumanReadableFormat(currentPath.length()) + "<br/>" +
                    "Modified: " + fileLastModHumanReadableFormat(currentPath.lastModified()) + "</p>" +
                    "<a href=\"/f?download=" + currentPath + "\">Download</a>";
        }
        // file list page
        for (File file : Objects.requireNonNull(currentPath.listFiles())) {
            String fileName = file.getName();
            String filePath = file.getAbsolutePath();
            if (file.isDirectory()) {
                filePaths.add("<b><a href=\"/f?p=" + filePath + "\">" + fileName + "/</a></b>");
            } else {
                filePaths.add("<a href=\"/f?p=" + filePath + "\">" + fileName + "</a>");
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<a href=\"/\">[HOME]</a>");
        if (currentPath.toString().equals("/")) {
            sb.append("<h3>/<h3>");
        } else {
            sb.append("<h3><a href=\"/f?p=" + currentPath.getParent() + "\">" + currentPath + "</a></h3>");

        }
        for (String filePathItem : filePaths) {
            sb.append(filePathItem);
        }
        return  sb.toString();
    }

    private Response fileListResponseResult(String path) {
        return newFixedLengthResponse(fileListHTML
                .replaceAll("RX_FILE_LIST", fileListHTMLReturn(path)));
    }

    private Response fileListDownload(String download) {
        try {
            FileInputStream fis = new FileInputStream(download);
            String mimeType = mimeTypeDetect(download);
            File file = new File(download);
            String filename = URLEncoder.encode(file.getName(), "UTF-8");
            String filenameString = String.format( "filename*=UTF-8''%s; filename=%s" , filename, filename);
            Response response;
            if (mimeType.matches("(text|image)/.*")) {
                // directly open text and image file
                response = newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length());
                response.addHeader("Content-Disposition", "inline;" + filenameString);
            } else {
                response = newFixedLengthResponse(Response.Status.OK, "application/octet-stream", fis, file.length());
                response.addHeader("Content-Disposition", "attachment;" + filenameString);
            }
            return response;
        } catch (FileNotFoundException | UnsupportedEncodingException e){
            e.printStackTrace();
        }
        return msgReturn(Response.Status.INTERNAL_ERROR, "<h1>fileListDownload: ERROR</h1>");
    }

    private Response uploadFormResponseResult() {
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", uploadFormHTML);
    }

    private Response postUploadFile(IHTTPSession session, Map<String, String> files) throws IOException {
        Map<String, String> header = session.getHeaders();
        Map<String, String> params = session.getParms();
        // String clientIp = header.get("http-client-ip");
        for (String key : header.keySet()) {
//            Log.d(TAG, "postUploadFile header###" + key + ": " + header.get(key));
        }
        // decode unicode char
        String fileName = URLDecoder.decode(params.get("file_name"), "UTF-8");
        /* if js not enable from client browser, we use origin file name, but
           it could be messy up! */
        if(fileName.equals("")) {
            fileName = params.get("file");
        }
        String contentLen = header.get("content-length");
        if (contentLen == null) {
            return msgReturn(Response.Status.BAD_REQUEST, "<h1>ZERO LENGTH FILE</h1>");
        }
        long contentLength = Long.parseLong(contentLen);
        File sdcardDownloadFolder = new File(MainActivity.getAccessPath());
        String tempFile = files.get("file");
        if (tempFile == null) {
            return msgReturn(Response.Status.INTERNAL_ERROR, "<h1>NULL FILE NAME</h1>");
        }
        File sourceTempFile = new File(tempFile);
        File destFile = new File(sdcardDownloadFolder.toString() + "/" + fileName);
        // limit max size to 1025 MiB
        if (contentLength > 1074790400 ) {
            return msgReturn(Response.Status.NOT_ACCEPTABLE,"<h1>FILE TOO LARGE</h1>");
        }
        if (contentLength > sdcardDownloadFolder.getFreeSpace()) {
            return msgReturn(Response.Status.NOT_ACCEPTABLE, "<h1>NO MORE SPACE ON SERVER</h1>");
        }
        if (destFile.exists()) {
            return msgReturn(Response.Status.NOT_ACCEPTABLE,
                    "<h1>Same Name File Already In Server.</h1>");
        }
        if (sourceTempFile.exists()) {
            copyFileUsingFileChannels(sourceTempFile, destFile);
            return msgReturn(Response.Status.OK, "<h1>Upload Succeeded</h1><p> The '" +
                    fileName + "' is save to <code>" + sdcardDownloadFolder + "</code></p><i>");
        }
        return msgReturn(Response.Status.INTERNAL_ERROR, "<h1>postUploadFile: ERROR</h1>");
    }

    private Response postSubmitText(IHTTPSession session) throws IOException {
        Map<String, String> params = session.getParms();
        String text = params.get("text");
        savedText = (text == null) ? "" : text;
        return textFormResponseResult(savedText);
    }

    private static List<String> extractUrl(String text) {
        List<String> containedUrls = new ArrayList<>();
        String urlRegex = "https?://\\S+";
        Pattern pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
        Matcher urlMatcher = pattern.matcher(text);
        while (urlMatcher.find()) {
            containedUrls.add(text.substring(urlMatcher.start(), urlMatcher.end()));
        }
        return containedUrls;
    }

    private static String makeLinks(List<String> extractedUrls) {
        if (extractedUrls.size() == 0) {
           return "";
        }
        StringBuilder links = new StringBuilder();
        for (String url : extractedUrls) {
            links.append("<a href=\"" + url + "\">" + url + "</a><br/>");
        }
        return links.toString();
    }

    private static void copyFileUsingFileChannels(File source, File dest) throws IOException {
        try (FileChannel inputChannel = new FileInputStream(source).getChannel();
             FileChannel outputChannel = new FileOutputStream(dest).getChannel()) {
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        }
    }
}
