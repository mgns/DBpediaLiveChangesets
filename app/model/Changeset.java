package model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.hp.hpl.jena.rdf.model.*;
import play.Logger;
import play.Play;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by magnus on 21.03.15.
 */
public class Changeset {

    private static final String iribase = "http://live.de.dbpedia.org/changesets/";
    //Play.application().configuration().getString("application.iribase");

    private String uri;
    private String start;
    private String end;

    private Multimap<Resource, Statement> removedMap;
    private Multimap<Resource, Statement> addedMap;

    public Changeset(String start, File removed, File added) {
        this(start, start);

        Multimap<Resource, Statement> removedStmtMap = getStatementMapFromFile(removed);
        Multimap<Resource, Statement> addedStmtMap = getStatementMapFromFile(added);

        assert(removedMap.isEmpty());
        assert(addedMap.isEmpty());

        if (removedStmtMap != null && addedStmtMap != null) {
            // might be faster: this.removedMap.putAll(removedStmtMap);
            removeAll(removedStmtMap.values());
            addAll(addedStmtMap.values());
        }
    }

    public Changeset(String start) {
        this(start, start);
    }

    public Changeset(String start, String end) {
        this.start = start;
        this.end = end;

        if (start.equals(end))
            this.uri = iribase + start;
        else
            this.uri = iribase + start + "-" + end;

        this.removedMap = HashMultimap.create();
        this.addedMap = HashMultimap.create();
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public Multimap<Resource, Statement> getRemovedMap() {
        return removedMap;
    }

    public Multimap<Resource, Statement> getAddedMap() {
        return addedMap;
    }

    public void remove(Statement stmt) {
        if (addedMap.get(stmt.getSubject()).contains(stmt)) {
            // stmt has been added before: un-add
            addedMap.remove(stmt.getSubject(), stmt);
        } else {
            // remove
            removedMap.put(stmt.getSubject(), stmt);
        }
    }

    public void add(Statement stmt) {
        if (removedMap.get(stmt.getSubject()).contains(stmt)) {
            // stmt has been removed before: un-remove
            removedMap.remove(stmt.getSubject(), stmt);
        } else {
            // add
            addedMap.put(stmt.getSubject(), stmt);
        }
    }

    public void removeAll(Collection<Statement> stmts) {
        for (Statement stmt : stmts) {
            remove(stmt);
        }
    }

    public void addAll(Collection<Statement> stmts) {
        for (Statement stmt : stmts) {
            add(stmt);
        }
    }

    public void appendChangeset(Changeset changeset) {
        removeAll(changeset.getRemovedMap().values());
        addAll(changeset.getAddedMap().values());
    }

    public Model getGuoModel() {
        Model guoModel = ModelFactory.createDefaultModel();

        Set<Resource> resources = new HashSet<Resource>();

        for (Resource r : removedMap.keySet()) {
            resources.add(r);
        }
        for (Resource r : addedMap.keySet()) {
            resources.add(r);
        }

        for (Resource resource : resources) {
            if (resource.getNameSpace().equals("http://de.dbpedia.org/resource/")) {
                Collection<Statement> removed = removedMap.get(resource);
                Collection<Statement> added = addedMap.get(resource);

                Logger.debug(" ++ Process " + resource + ": delete " + removed.size() + ", insert " + added.size());

                Resource update = guoModel.createResource(uri + "/" + resource.getNameSpace().split("//", 2)[1] + resource.getLocalName());
                Property pTargetSubject = guoModel.createProperty("http://webr3.org/owl/guo#", "target_subject");
                update.addProperty(pTargetSubject, resource);

                Resource updateGraph = guoModel.createResource();
                Property pDelete = guoModel.createProperty("http://webr3.org/owl/guo#", "delete");
                for (Statement stmt : removed) {
                    updateGraph.addProperty(stmt.getPredicate(), stmt.getObject());
                }
                update.addProperty(pDelete, updateGraph);

                Property pInsert = guoModel.createProperty("http://webr3.org/owl/guo#", "insert");
                for (Statement stmt : removed) {
                    updateGraph.addProperty(stmt.getPredicate(), stmt.getObject());
                }
                update.addProperty(pInsert, updateGraph);
            }
        }

        return guoModel;
    }

    private static Multimap<Resource, Statement> getStatementMapFromFile(File f) {
        Multimap<Resource, Statement> stmtMap = HashMultimap.create();

        try {
            Model fileModel = ModelFactory.createDefaultModel();
            fileModel.read(f.getAbsolutePath());

            StmtIterator iter = fileModel.listStatements();
            try {
                while (iter.hasNext()) {
                    Statement stmt = iter.next();
                    Resource s = stmt.getSubject();
                    // TODO blacklisting properties
                    //Property p = stmt.getPredicate();
                    //if (!p.getLocalName().equals("wikiPageExtracted")) {
                    stmtMap.put(s, stmt);
                    //}
                }
            } finally {
                if (iter != null) iter.close();
            }

        } catch (Exception e) {
            Logger.warn("BROKEN FILE " + f.getAbsolutePath() + ": " + e.getStackTrace());
            return null;
        }

        return stmtMap;
    }
}