package com.jd.biz.controller.rdb;

import com.jd.biz.controller.rdb.request.ConstraintInfoRequest;
import com.jd.biz.controller.rdb.request.DdlExportRequest;
import com.jd.biz.controller.rdb.request.FunctionDetailRequest;
import com.jd.biz.controller.rdb.request.FunctionUpdateRequest;
import com.jd.biz.controller.rdb.request.ProcedureDetailRequest;
import com.jd.biz.controller.rdb.request.ProcedureUpdateRequest;
import com.jd.biz.controller.rdb.request.TableDeleteRequest;
import com.jd.biz.controller.rdb.request.TableDetailQueryRequest;
import com.jd.biz.controller.rdb.request.TableImportRequest;
import com.jd.biz.controller.rdb.request.TableModifySqlRequest;
import com.jd.biz.controller.rdb.request.TableRequest;
import com.jd.biz.controller.rdb.request.TableSpaceCreateRequest;
import com.jd.biz.controller.rdb.request.TableSpaceUpdateRequest;
import com.jd.biz.controller.rdb.request.TriggerDetailRequest;
import com.jd.biz.controller.rdb.request.TypeQueryRequest;
import com.jd.biz.controller.rdb.request.ViewNewRequest;
import com.jd.biz.controller.rdb.request.ViewQueryRequest;
import com.jd.biz.controller.rdb.request.ViewRequest;
import com.jd.common.annotation.Log;
import com.jd.common.enums.BusinessType;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DdlOperationAuditAnnotationTest {

    @Test
    public void shouldAuditTableMutations() throws Exception {
        assertAudit(TableController.class, "copyTable", BusinessType.INSERT, TableDetailQueryRequest.class);
        assertAudit(TableController.class, "dropConstraint", BusinessType.DELETE, TypeQueryRequest.class);
        assertAudit(TableController.class, "createTableConstraint", BusinessType.INSERT, ConstraintInfoRequest.class);
        assertAudit(TableController.class, "delete", BusinessType.DELETE, TableDeleteRequest.class);
        assertAudit(TableController.class, "createTable", BusinessType.INSERT, TableRequest.class);
        assertAudit(TableController.class, "dropTable", BusinessType.DELETE, TableRequest.class);
        assertAudit(TableController.class, "dropTableTask", BusinessType.DELETE, TableRequest.class);

        Log importLog = assertAudit(TableController.class, "importTable", BusinessType.IMPORT,
                TableImportRequest.class);
        assertTrue(Arrays.asList(importLog.excludeParamNames()).contains("importUrl"));
    }

    @Test
    public void shouldAuditViewAndProgramObjectMutations() throws Exception {
        assertAudit(ViewController.class, "updateViewTableName", BusinessType.UPDATE, ViewQueryRequest.class);
        assertAudit(ViewController.class, "updateViewColumnName", BusinessType.UPDATE, ViewQueryRequest.class);
        assertAudit(ViewController.class, "delete", BusinessType.DELETE, ViewNewRequest.class);
        assertAudit(ViewController.class, "execute", BusinessType.EXECUTE_DATA, ViewRequest.class);
        assertAudit(ViewController.class, "allExecute", BusinessType.EXECUTE_DATA, ViewRequest.class);

        assertAudit(ProcedureController.class, "update", BusinessType.UPDATE, ProcedureUpdateRequest.class);
        assertAudit(ProcedureController.class, "deleteProcedure", BusinessType.DELETE, ProcedureDetailRequest.class);
        assertAudit(FunctionController.class, "update", BusinessType.UPDATE, FunctionUpdateRequest.class);
        assertAudit(FunctionController.class, "deleteFunction", BusinessType.DELETE, FunctionDetailRequest.class);
        assertAudit(TriggerController.class, "createTriggersWH", BusinessType.INSERT, TriggerDetailRequest.class);
        assertAudit(TriggerController.class, "deleteTriggers", BusinessType.DELETE, TriggerDetailRequest.class);
    }

    @Test
    public void shouldAuditTablespaceMutationsWithoutSavingPaths() throws Exception {
        Log create = assertAudit(TableSpaceController.class, "createTablespace", BusinessType.INSERT,
                TableSpaceCreateRequest.class);
        Log update = assertAudit(TableSpaceController.class, "updateTablespaceSql", BusinessType.UPDATE,
                TableSpaceUpdateRequest.class);
        Log drop = assertAudit(TableSpaceController.class, "dropTablespace", BusinessType.DELETE,
                TableSpaceCreateRequest.class);

        assertTrue(Arrays.asList(create.excludeParamNames()).contains("path"));
        assertTrue(Arrays.asList(update.excludeParamNames()).contains("path"));
        assertTrue(Arrays.asList(drop.excludeParamNames()).contains("path"));
    }

    @Test
    public void shouldNotAuditSqlPreviewEndpointsAsDatabaseChanges() throws Exception {
        assertNoAudit(TableController.class, "createTableSql", DdlExportRequest.class);
        assertNoAudit(TableController.class, "modifySql", TableModifySqlRequest.class);
        assertNoAudit(ViewController.class, "showSql", ViewRequest.class);
        assertNoAudit(ViewController.class, "getViewSql", ViewRequest.class);
        assertNoAudit(ProcedureController.class, "createProcedure", ProcedureDetailRequest.class);
        assertNoAudit(FunctionController.class, "createFunction", FunctionDetailRequest.class);
        assertNoAudit(TriggerController.class, "createTriggers", TriggerDetailRequest.class);
    }

    @Test
    public void shouldRejectCreateTableWithoutColumns() throws Exception {
        TableRequest request = new TableRequest();
        request.setName("EMPTY_TABLE");

        ActionResult result = new TableController().createTable(request);

        assertFalse(result.getSuccess());
        assertEquals("列信息不能为空", result.getErrorMessage());
    }

    private Log assertAudit(Class<?> controller, String methodName, BusinessType businessType,
                            Class<?>... parameterTypes) throws Exception {
        Method method = controller.getDeclaredMethod(methodName, parameterTypes);
        Log log = method.getAnnotation(Log.class);
        assertNotNull(controller.getSimpleName() + "." + methodName + " should be audited", log);
        assertEquals(businessType, log.businessType());
        return log;
    }

    private void assertNoAudit(Class<?> controller, String methodName, Class<?>... parameterTypes)
            throws Exception {
        Method method = controller.getDeclaredMethod(methodName, parameterTypes);
        assertNull(method.getAnnotation(Log.class));
    }
}
