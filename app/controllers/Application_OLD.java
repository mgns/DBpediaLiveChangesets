package controllers;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import model.Changeset;
import play.Logger;
import play.Play;
import play.libs.F.Function;
import play.libs.F.Promise;
import play.libs.F.Tuple;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;
import utils.Cache;
import utils.DBpediaLiveChangesetsUtil;
import views.html.index;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Application_OLD extends Controller {

    private static final Pattern PATTERN = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>(?:<[^>]+>)*?([^<>]+?)(?:<[^>]+>)*?</a>", Pattern.CASE_INSENSITIVE);

    public static Result index() {
        return ok(index.render("Your new application is ready."));
    }

    public static Promise<Result> changesets(int year, int month, int day, int hour, int i) {

        final String path = getPath(year, month, day, hour, i);

        Logger.info("Getting: " + path);

        if (month > 12)
            return Promise.pure((Result) Results.badRequest("Invalid parameter: month > 12"));
        if (day > 31)
            return Promise.pure((Result) Results.badRequest("Invalid parameter: day > 31"));
        if (hour > 24)
            return Promise.pure((Result) Results.badRequest("Invalid parameter: hour > 24"));
        if (year < 0 || month < 0 || day < 0)
            return Promise.pure(Results.TODO);

        Function<Tuple<Model,File>, Model> addFileToModelFunction = new Function<Tuple<Model,File>, Model>() {
            @Override
            public Model apply(Tuple<Model, File> modelFileTuple) throws Throwable {
                //Logger.debug("add File " + modelFileTuple._2.getAbsolutePath() + " to model " + modelFileTuple._1);

                Model model = DBpediaLiveChangesetsUtil.createUpdateModel(modelFileTuple._1, modelFileTuple._2);
                return model;
            }
        };

        Function<Tuple<File,File>, Changeset> addFileToChangesetFunction = new Function<Tuple<File,File>, Changeset>() {
            @Override
            public Changeset apply(Tuple<File, File> fileTuple) throws Throwable {
                //Logger.debug("add File " + modelFileTuple._2.getAbsolutePath() + " to model " + modelFileTuple._1);

                Changeset changeset = new Changeset(null, fileTuple._1, fileTuple._2);
                return changeset;
            }
        };

        // TODO implement routine for folders, i.e. hours, days, months

        Collection<Promise<File>> files = new ArrayList<>();

        if (i >= 0) {
            Promise<File> added = getFile(path + ".added.nt.gz");
            Promise<File> removed = getFile(path + ".removed.nt.gz");

            files.add(added);
            files.add(removed);
        } else if (i < 0) {
            try {
                files.addAll(getFiles(path));
            } catch (Throwable ignore) {
                Logger.warn("Get files exception: ", ignore);
            }
        } else {
            return Promise.pure((Result) Results.badRequest("Something went wrong."));
        }


        // create updateModel
        Promise<Model> updateModel = Promise.pure(ModelFactory.createDefaultModel());
        for (Promise<File> file : files) {
            updateModel = updateModel.zip(file).map(addFileToModelFunction);
        }

        return updateModel.map(
                new Function<Model, Result>() {
                    @Override
                    public Result apply(Model model) throws Throwable {

                        File tmpFile = new File(Cache.getTmpPath("results/" + path + ".guo.nt"));
                        tmpFile.getParentFile().mkdirs();
//                        model.write(new GZIPOutputStream(new FileOutputStream(tmpFile)), "TTL");
                        model.write(new FileOutputStream(tmpFile), "TTL");

                        return ok(tmpFile);
                    }
                }
        );
    }

    private static Collection<Promise<File>> getFiles(final String path) {
        final String base = Play.application().configuration().getString("dbpedia.live.changesets.base");
        String url = base + path;

        Logger.info("Getting URL: " + url);

        final Promise<Collection<Promise<File>>> promiseOfFiles = WS.url(url).setFollowRedirects(true).get().map(
                new Function<WSResponse, Collection<Promise<File>>>() {
                    @Override
                    public Collection<Promise<File>> apply(WSResponse response) throws Throwable {
                        Collection<Promise<File>> result = new ArrayList<Promise<File>>();
//                        Logger.debug("Content-Type: " + response.getHeader("Content-Type"));

                        if (response.getHeader("Content-Type").startsWith("text/html")) {
                            String htmlText = response.getBody();

                            List<String> urlList = new ArrayList<String>();
                            Matcher matcher = PATTERN.matcher(htmlText);

                            while (matcher.find()) {
                                String href = matcher.group(1);

                                if ((href == null)) {
                                    // the groups were not found (shouldn't happen, really)
                                    continue;
                                }

                                if (href.startsWith("http:") || href.startsWith("https:")) {
                                    if (!href.startsWith(path)) {
                                        Logger.info("Ignore URL: " + href);
                                        continue;
                                    }
                                    href = href.substring(path.length());
                                }

                                if (href.startsWith("../")) {
                                    // we are only interested in sub-URLs, not parent URLs, so skip this one
                                    continue;
                                }

                                // absolute href: convert to relative one
                                if (href.startsWith("/")) {
                                    int slashIndex = href.substring(0, href.length() - 1).lastIndexOf('/');
                                    href = href.substring(slashIndex + 1);
                                }

                                // relative to current href: convert to simple relative one
                                if (href.startsWith("./")) {
                                    href = href.substring("./".length());
                                }

                                String child = path + href;
                                urlList.add(child);
                            }

                            for (String childPath : urlList) {
                                boolean directory = childPath.endsWith("/");
                                if (directory) {
                                    result.addAll(getFiles(childPath));
                                    continue;
                                }

                                boolean changesetFile = childPath.endsWith(".added.nt.gz") || childPath.endsWith(".removed.nt.gz");
                                if (changesetFile) {
                                    Promise<File> file = getFile(childPath);
                                    result.add(file);
                                    continue;
                                }

                                Logger.info("Ignore URL: " + childPath);
                            }
                        }

                        return result;
                    }
                }
        );

        return promiseOfFiles.get(1, TimeUnit.HOURS);
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

}
