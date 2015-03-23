package controllers;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import model.Changeset;
import play.*;
import play.libs.F;
import play.libs.F.*;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.*;

import utils.Cache;
import utils.DBpediaLiveChangesetsUtil;
import views.html.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Application extends Controller {

    private static final Pattern PATTERN = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>(?:<[^>]+>)*?([^<>]+?)(?:<[^>]+>)*?</a>", Pattern.CASE_INSENSITIVE);

    public static Result index() {
        return ok(index.render("Your new application is ready."));
    }

    public static Promise<Result> changesets(int year, int month, int day, int hour, int i) {

        final String path = getPath(year, month, day, hour, i);

        Logger.info("Getting: " + path);

        if (month > 12)
            return Promise.pure((Result) play.mvc.Results.badRequest("Invalid parameter: month > 12"));
        if (day > 31)
            return Promise.pure((Result) play.mvc.Results.badRequest("Invalid parameter: day > 31"));
        if (hour > 24)
            return Promise.pure((Result) play.mvc.Results.badRequest("Invalid parameter: hour > 24"));
        if (year < 0 || month < 0 || day < 0)
            return Promise.pure(play.mvc.Results.TODO);

        Promise<Changeset> promiseOfChangeset = getChangeset(year, month, day, hour, i);

        if (promiseOfChangeset == null) {
            return Promise.pure((Result) Results.badRequest("Something went wrong."));
        }

        return promiseOfChangeset.map(
            new Function<Changeset, Result>() {
                @Override
                public Result apply(Changeset changeset) throws Throwable {
                    File tmpFile = new File(Cache.getTmpPath("results/" + path + ".guo.nt"));
                    tmpFile.getParentFile().mkdirs();
                    //model.write(new GZIPOutputStream(new FileOutputStream(tmpFile)), "TTL");
                    Model model = changeset.getGuoModel();
                    model.write(new FileOutputStream(tmpFile), "TTL");

                    return ok(tmpFile);
                }
            }
        );
    }

    private static Promise<Changeset> getChangeset(int year, int month, int day, int hour, int i) {
        final String path = getPath(year, month, day, hour, i);

        if (i >= 0) {
            final Promise<File> promiseOfRemoved = getFile(path + ".removed.nt.gz");
            final Promise<File> promiseOfAdded = getFile(path + ".added.nt.gz");

            ChangesetPromise csp = new ChangesetPromise(path, promiseOfRemoved, promiseOfAdded);
            Promise<Changeset> promiseOfChangeset = getPromiseOfChangeset(csp);

            return promiseOfChangeset;
        } else if (i < 0) {
            
        }

        return null;
    }

    private static Promise<Changeset> getPromiseOfChangeset(ChangesetPromise csp) {
        final String name = csp.name;
        final Promise<File> promiseOfRemoved = csp.removed;
        final Promise<File> promiseOfAdded = csp.added;

        Promise<Changeset> promiseOfChangeset = promiseOfRemoved.flatMap(
            new Function<File, Promise<Changeset>>() {
                @Override
                public Promise<Changeset> apply(final File removed) {
                    return promiseOfAdded.map(
                        new Function<File, Changeset>() {
                            @Override
                            public Changeset apply(File added) throws Throwable {
                                return new Changeset(name, removed, added);
                            }
                        }
                    );
                }
            }
        );

        return promiseOfChangeset;
    }

    private static Promise<File> getFile(final String path) {

        final String base = Play.application().configuration().getString("dbpedia.live.changesets.base");
        String url = base + path;

        File tmpFile = Cache.get(path);
        if (tmpFile != null) {
            return Promise.pure(tmpFile);
        } else {
            Logger.info("Getting URL: " + url);

            final Promise<File> promiseOfFile = WS.url(url).get().map(
                    new Function<WSResponse, File>() {
                        @Override
                        public File apply(WSResponse response) throws Throwable {
                            InputStream inputStream = null;
                            try {
//                            Logger.debug("Content-Type: " + response.getHeader("Content-Type"));

                                inputStream = response.getBodyAsStream();
                                File file = Cache.set(path, inputStream);
                                return file;
                            } catch (IOException e) {
                                throw e;
                            } finally {
                                if (inputStream != null) {
                                    inputStream.close();
                                }
                            }
                        }
                    }
            );

            return promiseOfFile;
        }
    }

    public static String getPath(int year, int month, int day, int hour, int i) {
        return String.format("%04d", year) + "/" +
                (month < 0 ? "" : String.format("%02d", month) + "/" +
                        (day < 0 ? "" : String.format("%02d", day) + "/" +
                                (hour < 0 ? "" : String.format("%02d", hour) + "/" +
                                        (i < 0 ? "" : String.format("%06d", i)
                                        )
                                )
                        )
                );
    }

    private static class ChangesetPromise {
        protected String name;
        protected Promise<File> removed;
        protected Promise<File> added;

        public ChangesetPromise(String name, Promise<File> removed, Promise<File> added) {
            this.name = name;
            this.removed = removed;
            this.added = added;
        }
    }

}
