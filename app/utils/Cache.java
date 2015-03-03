package utils;

import play.Logger;
import play.Play;

import java.io.*;
import java.nio.file.Paths;

/**
 * Created by magnus on 25.02.15.
 */
public class Cache {

    protected static final String tmp = Play.application().configuration().getString("cache.folder");

    public static File get(String path) {
        File cachedFile = new File(String.valueOf(Paths.get(tmp, path)));
        if (cachedFile.exists() && cachedFile.canRead() && cachedFile.isFile()) {
            Logger.info("HIT " + cachedFile.getAbsolutePath());
            return cachedFile;
        } else if (cachedFile.exists() && cachedFile.canRead() && cachedFile.isDirectory()) {
            return get(path + "index.html");
        }

        Logger.info("MISS " + cachedFile.getAbsolutePath());
        return null;
    }

    public static File set(String path, InputStream inputStream) throws IOException {
        File cachedFile = new File(getTmpPath(path));
        Logger.info("Set cache file: " + cachedFile.getAbsolutePath());

        OutputStream outputStream = null;
        try {
            cachedFile.getParentFile().mkdirs();

            outputStream = new FileOutputStream(cachedFile);

            int read = 0;
            byte[] buffer = new byte[1024];

            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {inputStream.close();}
            if (outputStream != null) {outputStream.close();}
        }
        return cachedFile;
    }

    public static String getTmpPath(String path) {
        return String.valueOf(Paths.get(tmp, path));
    }

}
