package controllers;

import org.apache.commons.io.FilenameUtils;
import play.*;
import play.libs.F.*;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.*;

import utils.DBpediaLiveChangesetsUtil;
import views.html.*;

import java.io.*;

public class Application extends Controller {

    public static Result index() {
        return ok(index.render("Your new application is ready."));
    }

    public static Result changesets(int year, int month, int day, int hour, int i) {

        String path = getPath(year, month, day, hour, i);

        Logger.info("Getting: " + path);

        if (month > 12)
            return play.mvc.Results.badRequest("Invalid parameter: month > 12");
        if (day > 31)
            return play.mvc.Results.badRequest("Invalid parameter: day > 31");
        if (hour > 24)
            return play.mvc.Results.badRequest("Invalid parameter: hour > 24");
        if (year < 0 || month < 0 || day < 0 || hour < 0 || i < 0)
            return play.mvc.Results.TODO;

        DBpediaLiveChangesetsUtil.getChangesets(path);

        return play.mvc.Results.TODO;
    }

    public static Promise<Result> added(int year, int month, int day, int hour, int i) {

        String path = getPath(year, month, day, hour, i);

        Logger.info("Getting: " + path);

        final String base = Play.application().configuration().getString("dbpedia.live.changesets.base");
        String url = base + path + ".added.nt.gz";

        Logger.info(url);

        final Promise<Result> promiseOfFile = WS.url(url).get().map(
                new Function<WSResponse, Result>() {
                    @Override
                    public Result apply(WSResponse response) throws Throwable {
                        InputStream inputStream = null;
                        OutputStream outputStream = null;
                        try {
                            inputStream = response.getBodyAsStream();

                            // write the inputStream to a File
                            final File file = new File("/tmp/" + response.getUri().getPath());
                            file.getParentFile().mkdirs();
                            outputStream = new FileOutputStream(file);

                            int read = 0;
                            byte[] buffer = new byte[1024];

                            while ((read = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, read);
                            }

                            return ok(file);
                        } catch (IOException e) {
                            throw e;
                        } finally {
                            if (inputStream != null) {inputStream.close();}
                            if (outputStream != null) {outputStream.close();}
                        }
                    }
                }
        );

        return promiseOfFile;
    }

    public static String getPath(int year, int month, int day, int hour, int i) {
        return String.format("%04d", year) +
                (month < 0 ? "" : "/" + String.format("%02d", month) +
                        (day < 0 ? "" : "/" + String.format("%02d", day) +
                                (hour < 0 ? "" : "/" + String.format("%02d", hour) +
                                        (i < 0 ? "" : "/" + String.format("%06d", i)
                                        )
                                )
                        )
                );
    }

}
