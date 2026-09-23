package com.jd.biz.domain.core.impl;

import com.jd.spi.model.AssociationTree;
import com.jd.spi.model.ForeignData;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TableServiceImplRelationshipTest {

    @Test
    public void shouldGroupCompositeForeignKeyColumnsInPositionOrder() {
        List<AssociationTree> relations = TableServiceImpl.buildReferencedAssociations(Arrays.asList(
                foreignKey("CHILD_TABLE", "FK_CHILD_PARENT", "PARENT_ID_1", "CHILD_ID_1"),
                foreignKey("CHILD_TABLE", "FK_CHILD_PARENT", "PARENT_ID_2", "CHILD_ID_2"),
                foreignKey("OTHER_CHILD", "FK_OTHER_PARENT", "PARENT_ID_1", "PARENT_ID")));

        assertEquals(2, relations.size());
        AssociationTree compositeRelation = relations.get(0);
        assertEquals("FK_CHILD_PARENT", compositeRelation.getConstraintName());
        assertEquals("PARENT_ID_1, PARENT_ID_2", compositeRelation.getColumn());
        assertEquals("CHILD_ID_1, CHILD_ID_2", compositeRelation.getForeignColumnName());
    }

    private ForeignData foreignKey(String tableName, String constraintName,
                                   String parentColumn, String childColumn) {
        ForeignData foreignData = new ForeignData();
        foreignData.setForeignSchemaName("SYSTEM");
        foreignData.setForeignTableName(tableName);
        foreignData.setConstraintName(constraintName);
        foreignData.setColumn(parentColumn);
        foreignData.setForeignColumnName(childColumn);
        foreignData.setStatus("ENABLED");
        return foreignData;
    }
}
