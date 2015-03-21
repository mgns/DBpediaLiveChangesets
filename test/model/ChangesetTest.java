package model;

import com.hp.hpl.jena.rdf.model.Model;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/**
 * Created by magnus on 21.03.15.
 */
public class ChangesetTest {

    @Test
    public void testLoadFromFile() {

        File removed = new File("/Users/magnus/Datasets/live.dbpedia.org/changesets/2015/03/19/23/000371.removed.nt.gz");
        File added = new File("/Users/magnus/Datasets/live.dbpedia.org/changesets/2015/03/19/23/000371.added.nt.gz");

        Changeset cs = new Changeset("2015-03-19-23-000371", removed, added);

        Assert.assertEquals("2015-03-19-23-000371", cs.getStart());
        Assert.assertEquals("2015-03-19-23-000371", cs.getEnd());

        Model guoModel = cs.getGuoModel();
        guoModel.write(System.out, "TURTLE");
    }

    @Test
    public void testLoadFromFileUnRemove() {

        File removed = new File("/Users/magnus/Datasets/live.dbpedia.org/changesets/2015/03/19/23/000370.removed.nt.gz");
        File added = new File("/Users/magnus/Datasets/live.dbpedia.org/changesets/2015/03/19/23/000370.removed.nt.gz");

        Changeset cs = new Changeset("2015-03-19-23-000370", removed, added);

        Assert.assertEquals("2015-03-19-23-000370", cs.getStart());
        Assert.assertEquals("2015-03-19-23-000370", cs.getEnd());

        Assert.assertTrue(cs.getRemovedMap().isEmpty());
        Assert.assertTrue(cs.getAddedMap().isEmpty());

        Model guoModel = cs.getGuoModel();
        guoModel.write(System.out, "TURTLE");
    }

}
