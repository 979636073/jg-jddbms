package com.jd.biz.migration;

import com.jd.common.tools.base.excption.BusinessException;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationModel.*;

public class MigrationDialectTest {
    static Column column(String type, Integer precision, Integer scale, int length) {
        Column column = new Column(); column.name = "字段"; column.sourceType = type;
        column.precision = precision; column.scale = scale; column.length = length; column.nullable = true; return column;
    }
    static Config config() {
        Config config = new Config(); config.name = "QA"; config.sourceId = 1L; config.targetId = 2L;
        config.sourceSchema = "QA_SOURCE"; config.targetSchema = "QA_TARGET";
        Mapping mapping = new Mapping(); mapping.source = "ORDER"; mapping.target = "Target \"表"; config.tables.add(mapping); return config;
    }
    @Test public void quotesIdentifiersAndLiteralSeparately() {
        assertEquals("\"Target \"\"表\"", MigrationDialect.quote("Target \"表"));
        assertEquals("'a''; DROP TABLE x;--'", MigrationDialect.literal("a'; DROP TABLE x;--"));
        assertEquals(" DEFAULT 'a''; DROP TABLE x;--'", MigrationDialect.defaultSql("'a''; DROP TABLE x;--'"));
    }
    @Test public void mapsCommonTypesInAllFourDirections() {
        for (String source : new String[] { "DM", "ORACLE" }) for (String target : new String[] { "DM", "ORACLE" }) {
            Column number = column("NUMBER", 28, 9, 0); MigrationDialect.mapType(number, source, target); assertEquals("NUMBER(28,9)", number.targetType);
            Column date = column("DATE", null, null, 0); MigrationDialect.mapType(date, source, target); assertEquals("TIMESTAMP(0)", date.targetType);
            Column text = column("VARCHAR2", null, null, 100); text.unit = "CHAR"; MigrationDialect.mapType(text, source, target);
            assertEquals((target.equals("DM") ? "VARCHAR" : "VARCHAR2") + "(100 CHAR)", text.targetType);
            for (String type : new String[] { "BLOB", "CLOB", "NCLOB" }) { Column lob = column(type, null, null, 0); MigrationDialect.mapType(lob, source, target); assertEquals(type, lob.targetType); }
        }
    }

    @Test public void mapsDmDatetimeWithoutLosingFractionalSeconds() {
        for (String target : new String[] { "DM", "ORACLE" }) {
            Column datetime = column("DATETIME", null, 6, 0);
            MigrationDialect.mapType(datetime, "DM", target);
            assertEquals("TIMESTAMP", datetime.kind);
            assertEquals("TIMESTAMP(6)", datetime.targetType);
        }
        Column seconds = column("DATETIME", null, 0, 0);
        MigrationDialect.mapType(seconds, "DM", "ORACLE");
        assertEquals("TIMESTAMP(0)", seconds.targetType);
        try { MigrationDialect.mapType(column("DATETIME", null, 9, 0), "DM", "ORACLE"); fail(); }
        catch (BusinessException expected) { }
    }
    @Test public void mapsIntegerWithoutAccidentallyLosingItsScale() {
        Column number = column("BIGINT", null, null, 0); MigrationDialect.mapType(number, "DM", "ORACLE"); assertEquals("NUMBER(19,0)", number.targetType);
    }
    @Test public void defaultsDmTextToClobAndAllowsConfirmedNclob() {
        for (String target : new String[] { "DM", "ORACLE" }) {
            Column lob = column("TEXT", null, null, 0); MigrationDialect.mapType(lob, "DM", target);
            assertEquals("CLOB", lob.kind); assertEquals("CLOB", lob.targetType);
            FieldMapping mapping = new FieldMapping(); mapping.source = lob.name; mapping.targetType = "NCLOB";
            try { MigrationConversion.apply(column("TEXT", null, null, 0), mapping, "DM", target); fail(); }
            catch (BusinessException expected) { }
            mapping.conversionConfirmed = true;
            Column national = column("TEXT", null, null, 0);
            MigrationConversion.apply(national, mapping, "DM", target);
            assertEquals("NCLOB", national.kind); assertEquals("NCLOB", national.targetType);
            assertNull(national.sourceExpression);
        }
        try { MigrationDialect.mapType(column("TEXT", null, null, 0), "ORACLE", "DM"); fail(); }
        catch (BusinessException expected) { }
    }
    @Test public void dmTargetTextMetadataMatchesConfirmedNclobOnlyForThatField() {
        Column planned = column("NCLOB", null, null, 0); planned.name = "NAT";
        MigrationDialect.mapType(planned, "ORACLE", "DM");
        Column actual = column("TEXT", null, null, 0); actual.name = "NAT";
        MigrationDialect.mapType(actual, "DM", "DM");
        MigrationMetadata.matchDmNclob(java.util.Collections.singletonList(planned), java.util.Collections.singletonList(actual), "DM");
        MigrationMetadata.compatible(java.util.Collections.singletonList(planned), java.util.Collections.singletonList(actual));
        assertEquals("NCLOB", actual.targetType);
    }
    @Test public void blocksUnsupportedTypesAndLossyPrecision() {
        for (String type : new String[] { "BFILE", "TIMESTAMP(6) WITH TIME ZONE", "BINARY_FLOAT", "MY_TYPE" }) {
            try { MigrationDialect.mapType(column(type, null, null, 0), "ORACLE", "DM"); fail(type); } catch (BusinessException expected) { }
        }
        try { MigrationDialect.mapType(column("NUMBER", 38, -2, 0), "ORACLE", "DM"); fail(); } catch (BusinessException expected) { }
        try { MigrationDialect.mapType(column("TIMESTAMP(9)", null, 9, 0), "ORACLE", "DM"); fail(); } catch (BusinessException expected) { }
        try { MigrationDialect.mapType(column("VARCHAR", null, null, 5000), "DM", "ORACLE"); fail(); } catch (BusinessException expected) { }
    }
    @Test public void rejectsExpressionDefaultsAndDestructiveStrategies() {
        for (String value : new String[] { "SYSDATE", "1); DROP TABLE X;--", "SEQ.NEXTVAL" }) {
            try { MigrationDialect.defaultSql(value); fail(); } catch (BusinessException expected) { }
        }
        Config config = config(); config.conflict = "TRUNCATE";
        try { MigrationDialect.validate(config, true); fail(); } catch (BusinessException expected) { }
    }
    @Test public void rejectsDuplicateMappingsAndSourceOverlap() {
        Config config = config(); config.tables.add(config.tables.get(0));
        try { MigrationDialect.validate(config, true); fail(); } catch (BusinessException expected) { }
        config = config(); config.targetId = config.sourceId; config.targetSchema = config.sourceSchema;
        Mapping mapping = new Mapping(); mapping.source = "B"; mapping.target = "ORDER"; config.tables.add(mapping);
        try { MigrationDialect.validate(config, true); fail(); } catch (BusinessException expected) { }
    }
    @Test public void acceptsMoreThanOneHundredObjects() {
        Config config = config();
        for (int index = 1; index < 125; index++) {
            Mapping mapping = new Mapping(); mapping.source = "SOURCE_" + index; mapping.target = "TARGET_" + index; config.tables.add(mapping);
        }
        MigrationDialect.validate(config, true);
        assertEquals(125, config.tables.size());
    }
    @Test public void targetCompatibilityChecksPrecisionNullabilityAndMissingColumns() {
        Column source = column("NUMBER", 20, 2, 0), target = column("NUMBER", 20, 2, 0);
        MigrationDialect.mapType(source, "ORACLE", "DM"); MigrationDialect.mapType(target, "DM", "DM");
        MigrationMetadata.compatible(java.util.Collections.singletonList(source), java.util.Collections.singletonList(target));
        target.nullable = false;
        try { MigrationMetadata.compatible(java.util.Collections.singletonList(source), java.util.Collections.singletonList(target)); fail(); } catch (BusinessException expected) { }
    }
}
