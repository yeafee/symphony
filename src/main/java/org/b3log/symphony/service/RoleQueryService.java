/*
 * Symphony - A modern community (forum/SNS/blog) platform written in Java.
 * Copyright (C) 2012-2016,  b3log.org & hacpai.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.service;

import org.b3log.latke.Keys;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Pagination;
import org.b3log.latke.repository.Query;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.SortDirection;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.CollectionUtils;
import org.b3log.latke.util.Paginator;
import org.b3log.symphony.model.Permission;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.repository.PermissionRepository;
import org.b3log.symphony.repository.RolePermissionRepository;
import org.b3log.symphony.repository.RoleRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Role query service.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.2.0.0, Dec 8, 2016
 * @since 1.8.0
 */
@Service
public class RoleQueryService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(RoleQueryService.class);

    /**
     * Role repository.
     */
    @Inject
    private RoleRepository roleRepository;

    /**
     * Role-Permission repository.
     */
    @Inject
    private RolePermissionRepository rolePermissionRepository;

    /**
     * Permission repository.
     */
    @Inject
    private PermissionRepository permissionRepository;

    /**
     * Gets an role specified by the given role id.
     *
     * @param roleId the given role id
     * @return an role, returns {@code null} if not found
     */
    public JSONObject getRole(final String roleId) {
        try {
            return roleRepository.get(roleId);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Gets role failed", e);

            return null;
        }
    }

    /**
     * Gets all permissions and marks grant of an role specified by the given role id.
     *
     * @param roleId the given role id
     * @return a list of permissions, returns an empty list if not found
     */
    public List<JSONObject> getPermissionsGrant(final String roleId) {
        final List<JSONObject> ret = new ArrayList<>();

        try {
            final List<JSONObject> permissions = CollectionUtils.jsonArrayToList(
                    permissionRepository.get(new Query()).optJSONArray(Keys.RESULTS));
            final List<JSONObject> rolePermissions = rolePermissionRepository.getByRoleId(roleId);

            for (final JSONObject permission : permissions) {
                final String permissionId = permission.optString(Keys.OBJECT_ID);
                permission.put(Permission.PERMISSION_T_GRANT, false);
                ret.add(permission);

                for (final JSONObject rolePermission : rolePermissions) {
                    final String grantPermissionId = rolePermission.optString(Permission.PERMISSION_ID);

                    if (permissionId.equals(grantPermissionId)) {
                        permission.put(Permission.PERMISSION_T_GRANT, true);

                        break;
                    }
                }
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Gets permissions grant of role [id=" + roleId + "] failed", e);
        }

        return ret;
    }

    /**
     * Gets permissions of an role specified by the given role id.
     *
     * @param roleId the given role id
     * @return a list of permissions, returns an empty list if not found
     */
    public List<JSONObject> getPermissions(final String roleId) {
        final List<JSONObject> ret = new ArrayList<>();

        try {
            final List<JSONObject> rolePermissions = rolePermissionRepository.getByRoleId(roleId);
            for (final JSONObject rolePermission : rolePermissions) {
                final String permissionId = rolePermission.optString(Permission.PERMISSION_ID);
                final JSONObject permission = permissionRepository.get(permissionId);

                ret.add(permission);
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Gets permissions of role [id=" + roleId + "] failed", e);
        }

        return ret;
    }

    /**
     * Gets roles by the specified request json object.
     *
     * @param currentPage the specified current page number
     * @param pageSize    the specified page size
     * @param windowSize  the specified window size
     * @return for example, <pre>
     * {
     *     "pagination": {
     *         "paginationPageCount": 100,
     *         "paginationPageNums": [1, 2, 3, 4, 5]
     *     },
     *     "roles": [{
     *         "oId": "",
     *         "roleName": "",
     *         "roleDescription": "",
     *         "permissions": [
     *             {
     *                 "oId": "adUpdateADSide",
     *                 "permissionCategory": int
     *             }, ....
     *         ]
     *     }, ....]
     * }
     * </pre>
     * @throws ServiceException service exception
     * @see Pagination
     */
    public JSONObject getRoles(final int currentPage, final int pageSize, final int windowSize)
            throws ServiceException {
        final JSONObject ret = new JSONObject();

        final Query query = new Query().setCurrentPageNum(currentPage).setPageSize(pageSize).
                addSort(Keys.OBJECT_ID, SortDirection.DESCENDING);

        JSONObject result = null;

        try {
            result = roleRepository.get(query);
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Gets roles failed", e);

            throw new ServiceException(e);
        }

        final int pageCount = result.optJSONObject(Pagination.PAGINATION).optInt(Pagination.PAGINATION_PAGE_COUNT);

        final JSONObject pagination = new JSONObject();
        ret.put(Pagination.PAGINATION, pagination);
        final List<Integer> pageNums = Paginator.paginate(currentPage, pageSize, pageCount, windowSize);
        pagination.put(Pagination.PAGINATION_PAGE_COUNT, pageCount);
        pagination.put(Pagination.PAGINATION_PAGE_NUMS, pageNums);

        final JSONArray data = result.optJSONArray(Keys.RESULTS);
        final List<JSONObject> roles = CollectionUtils.<JSONObject>jsonArrayToList(data);

        try {
            for (final JSONObject role : roles) {
                final List<JSONObject> permissions = new ArrayList<>();
                role.put(Permission.PERMISSIONS, (Object) permissions);

                final String roleId = role.optString(Keys.OBJECT_ID);
                final List<JSONObject> rolePermissions = rolePermissionRepository.getByRoleId(roleId);
                for (final JSONObject rolePermission : rolePermissions) {
                    final String permissionId = rolePermission.optString(Permission.PERMISSION_ID);
                    final JSONObject permission = permissionRepository.get(permissionId);

                    permissions.add(permission);
                }
            }
        } catch (final RepositoryException e) {
            LOGGER.log(Level.ERROR, "Gets role permissions failed", e);

            throw new ServiceException(e);
        }

        Collections.sort(roles, new Comparator<JSONObject>() {
            @Override
            public int compare(final JSONObject o1, final JSONObject o2) {
                return ((List) o2.opt(Permission.PERMISSIONS)).size()
                        - ((List) o1.opt(Permission.PERMISSIONS)).size();
            }
        });

        ret.put(Role.ROLES, (Object) roles);

        return ret;
    }
}
