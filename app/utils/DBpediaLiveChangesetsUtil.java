package utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.hp.hpl.jena.rdf.model.*;
import play.Logger;
import play.Play;

import java.io.File;
import java.util.Collection;

/**
 * Created by magnus on 25.02.15.
 */
public class DBpediaLiveChangesetsUtil {

    public static String base = Play.application().configuration().getString("dbpedia.live.changesets.base");
    public static String iribase = Play.application().configuration().getString("application.iribase");
    public static String addedSuffix = ".added.nt.gz";
    public static String removedSuffix = ".removed.nt.gz";

/*    public static Model getChangesets(String path) {

        File tmpAdded = getFile(path + addedSuffix);
        File tmpRemoved = getFile(path + addedSuffix);

        Model model = createUpdateModel(tmpAdded);

        return model;
    }*/

    public static Model createUpdateModel(File file) {
        return createUpdateModel(ModelFactory.createDefaultModel(), file);
    }

    public static Model createUpdateModel(Model updateModel, File file) {
        if (file.isDirectory()) {
            Logger.debug("DIR  " + file.getAbsolutePath());
            for (File f: file.listFiles()) {
                Model model = createUpdateModel(f);

                // TODO more intelligent merge
                updateModel.add(model);
            }
        }
        if (file.isFile() && file.canRead()) {

            if (file.getName().endsWith(".added.nt.gz")) {
                Logger.debug("READ FILE " + file.getAbsolutePath());

                Property pInsert = updateModel.createProperty("http://webr3.org/owl/guo#", "insert");
                processFile(updateModel, file, pInsert);

            } else if (file.getName().endsWith(".removed.nt.gz")) {
                Logger.debug("READ FILE " + file.getAbsolutePath());

                Property pDelete = updateModel.createProperty("http://webr3.org/owl/guo#", "delete");
                processFile(updateModel, file, pDelete);

            } else {
                Logger.warn("IGNORE FILE " + file.getAbsolutePath());
            }
        }

        return updateModel;
    }

    private static void processFile(Model updateModel, File f, Property action) {
        try {
            Model addModel = ModelFactory.createDefaultModel();
            addModel.read(f.getAbsolutePath());

            Multimap<Resource, Statement> stmtMap = HashMultimap.create();

            StmtIterator iter = addModel.listStatements();
            try {
                while (iter.hasNext()) {
                    Statement stmt = iter.next();
                    Resource s = stmt.getSubject();
                    Property p = stmt.getPredicate();
                    if (!p.getLocalName().equals("wikiPageExtracted")) {
                        stmtMap.put(s, stmt);
                    }
                }
            } finally {
                if (iter != null) iter.close();
            }

            for (Resource res : stmtMap.keySet()) {
                if (res.getNameSpace().equals("http://de.dbpedia.org/resource/")) {

                    Logger.debug("Processing " + res.getURI());

                    Collection<Statement> stmts = stmtMap.get(res);

                    Resource update = updateModel.createResource(iribase + f.getName().split("\\.")[0] + "/" + res.getNameSpace().split("//", 2)[1] + res.getLocalName());
                    Property pTargetSubject = updateModel.createProperty("http://webr3.org/owl/guo#", "target_subject");
                    update.addProperty(pTargetSubject, res);

                    Resource updateGraph = updateModel.createResource();

                    for (Statement stmt : stmts) {
                        updateGraph.addProperty(stmt.getPredicate(), stmt.getObject());
                    }

                    update.addProperty(action, updateGraph);
                }
            }
        } catch (Exception e) {
            Logger.warn("BROKEN FILE " + f.getAbsolutePath() + ": " + e.getStackTrace());
        }
    }

}
