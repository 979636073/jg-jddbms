package com.jd.biz.controller.data.source;

import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.service.DataSourceAccessBusinessService;
import com.jd.biz.domain.core.converter.DataSourceConverter;
import com.jd.biz.domain.repository.entity.DataSourceDO;
import com.jd.biz.domain.repository.mapper.DataSourceMapper;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.common.util.ContextUtils;
import com.jd.common.tools.common.exception.DataNotFoundException;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/connection/datasource/sharing/{id}")
public class DataSourceSharingController {
    @Autowired
    private DataSourceMapper dataSourceMapper;
    @Autowired
    private DataSourceConverter dataSourceConverter;
    @Autowired
    private DataSourceAccessBusinessService accessService;
    @Autowired
    private JdbcTemplate jdbc;

    private DataSource managed(Long id) {
        DataSourceDO row = dataSourceMapper.selectById(id);
        if (row == null) throw new DataNotFoundException();
        DataSource source = dataSourceConverter.do2summary(row);
        accessService.checkActionPermission(source, DataSourceAction.DATASOURCE_MANAGE);
        return source;
    }

    @GetMapping
    public DataResult<Map<String, Object>> list(@PathVariable Long id) {
        DataSource source = managed(id);
        List<Map<String, Object>> grants = jdbc.query(
                "SELECT a.ACCESS_OBJECT_TYPE, a.ACCESS_OBJECT_ID, a.PERMISSION_CODES, "
                        + "u.USER_NAME, u.NICK_NAME, t.NAME, t.DESCRIPTION FROM DATA_SOURCE_ACCESS a "
                        + "LEFT JOIN SYS_USER u ON a.ACCESS_OBJECT_TYPE = 'USER' AND a.ACCESS_OBJECT_ID = u.USER_ID "
                        + "LEFT JOIN TEAM t ON a.ACCESS_OBJECT_TYPE = 'TEAM' AND a.ACCESS_OBJECT_ID = t.ID "
                        + "WHERE a.DATA_SOURCE_ID = ? ORDER BY a.ID",
                (rs, row) -> {
                    Map<String, Object> grant = new LinkedHashMap<>();
                    boolean user = "USER".equals(rs.getString(1));
                    grant.put("objectType", rs.getString(1));
                    grant.put("objectId", rs.getLong(2));
                    grant.put("permissionCodes", rs.getString(3) == null
                            ? DataSourceAction.DEFAULT_GRANT : DataSourceAction.parse(rs.getString(3)));
                    grant.put("name", rs.getString(user ? 4 : 6));
                    grant.put("description", rs.getString(user ? 5 : 7));
                    return grant;
                }, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", source.getKind());
        result.put("grants", grants);
        return DataResult.of(result);
    }

    @GetMapping("/users")
    public DataResult<List<Map<String, Object>>> users(@PathVariable Long id, @RequestParam String keyword) {
        managed(id);
        if (keyword.trim().isEmpty()) return DataResult.of(Collections.emptyList());
        return DataResult.of(jdbc.query(
                "SELECT USER_ID, USER_NAME, NICK_NAME FROM SYS_USER WHERE DEL_FLAG = '0' AND STATUS = '0' "
                        + "AND (USER_NAME LIKE ? OR NICK_NAME LIKE ?) ORDER BY USER_ID FETCH FIRST 50 ROWS ONLY",
                (rs, row) -> {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("userId", rs.getLong(1));
                    user.put("userName", rs.getString(2));
                    user.put("nickName", rs.getString(3));
                    return user;
                }, "%" + keyword.trim() + "%", "%" + keyword.trim() + "%"));
    }

    @GetMapping("/teams")
    public DataResult<List<Map<String, Object>>> teams(@PathVariable Long id, @RequestParam String keyword) {
        managed(id);
        if (keyword.trim().isEmpty()) return DataResult.of(Collections.emptyList());
        return DataResult.of(jdbc.query(
                "SELECT ID, CODE, NAME FROM TEAM WHERE STATUS = 'VALID' AND (CODE LIKE ? OR NAME LIKE ?) "
                        + "ORDER BY ID FETCH FIRST 50 ROWS ONLY",
                (rs, row) -> {
                    Map<String, Object> team = new LinkedHashMap<>();
                    team.put("teamId", rs.getLong(1));
                    team.put("code", rs.getString(2));
                    team.put("name", rs.getString(3));
                    return team;
                }, "%" + keyword.trim() + "%", "%" + keyword.trim() + "%"));
    }

    @Data
    public static class GrantRequest {
        private Long userId;
        private Long teamId;
        private Set<String> permissionCodes;
    }

    @PostMapping("/users")
    @Transactional
    public ActionResult grantUser(@PathVariable Long id, @RequestBody GrantRequest request) {
        return grant(id, "USER", request.getUserId(), request.getPermissionCodes());
    }

    @PostMapping("/teams")
    @Transactional
    public ActionResult grantTeam(@PathVariable Long id, @RequestBody GrantRequest request) {
        return grant(id, "TEAM", request.getTeamId(), request.getPermissionCodes());
    }

    private ActionResult grant(Long id, String type, Long objectId, Set<String> actions) {
        managed(id);
        if (objectId == null) throw new BusinessException("请选择授权对象");
        String codes;
        try { codes = DataSourceAction.serialize(actions); }
        catch (IllegalArgumentException invalid) { throw new BusinessException("无效的操作权限"); }
        String existsSql = "USER".equals(type)
                ? "SELECT COUNT(*) FROM SYS_USER WHERE USER_ID = ? AND DEL_FLAG = '0' AND STATUS = '0'"
                : "SELECT COUNT(*) FROM TEAM WHERE ID = ? AND STATUS = 'VALID'";
        if (jdbc.queryForObject(existsSql, Integer.class, objectId) == 0) {
            throw new BusinessException("授权对象不存在或已停用");
        }
        Long user = ContextUtils.getUserId();
        int updated = jdbc.update("UPDATE DATA_SOURCE_ACCESS SET PERMISSION_CODES = ?, MODIFIED_USER_ID = ?, "
                        + "GMT_MODIFIED = CURRENT_TIMESTAMP WHERE DATA_SOURCE_ID = ? AND ACCESS_OBJECT_TYPE = ? AND ACCESS_OBJECT_ID = ?",
                codes, user, id, type, objectId);
        if (updated == 0) {
            jdbc.update("INSERT INTO DATA_SOURCE_ACCESS (DATA_SOURCE_ID, ACCESS_OBJECT_TYPE, ACCESS_OBJECT_ID, "
                            + "PERMISSION_CODES, CREATE_USER_ID, MODIFIED_USER_ID, GMT_CREATE, GMT_MODIFIED) "
                            + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    id, type, objectId, codes, user, user);
        }
        jdbc.update("UPDATE DATA_SOURCE SET KIND = 'SHARED' WHERE ID = ?", id);
        return ActionResult.isSuccess();
    }

    @DeleteMapping("/users/{userId}")
    public ActionResult revokeUser(@PathVariable Long id, @PathVariable Long userId) {
        return revoke(id, "USER", userId);
    }

    @DeleteMapping("/teams/{teamId}")
    public ActionResult revokeTeam(@PathVariable Long id, @PathVariable Long teamId) {
        return revoke(id, "TEAM", teamId);
    }

    private ActionResult revoke(Long id, String type, Long objectId) {
        managed(id);
        jdbc.update("DELETE FROM DATA_SOURCE_ACCESS WHERE DATA_SOURCE_ID = ? AND ACCESS_OBJECT_TYPE = ? AND ACCESS_OBJECT_ID = ?",
                id, type, objectId);
        return ActionResult.isSuccess();
    }
}
