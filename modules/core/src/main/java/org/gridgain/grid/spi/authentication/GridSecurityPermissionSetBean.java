/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.authentication;

import org.gridgain.grid.security.*;

import java.util.*;

/**
 * Permissions object detached from subject.
 * // TODO make java bean.
 */
public class GridSecurityPermissionSetBean implements GridSecurityPermissionSet {
    /** */
    private static final long serialVersionUID = 0L;

    /** Default allow all flag. */
    private boolean dfltAllowAll;

    /** Task permissions. */
    private Map<String, Collection<GridSecurityPermission>> taskPermissions = Collections.emptyMap();

    /** Cache permissions. */
    private Map<String, Collection<GridSecurityPermission>> cachePermissions = Collections.emptyMap();

    /**
     * Default constructor (forbids all).
     */
    public GridSecurityPermissionSetBean() {
        // No-op.
    }

    /**
     * @param dfltAllowAll Default allow all flag.
     */
    public GridSecurityPermissionSetBean(boolean dfltAllowAll) {
        this.dfltAllowAll = dfltAllowAll;
    }

    /**
     * Creates permission set with all task and cache permissions.
     *
     * @param dfltAllowAll Default allow all flag.
     * @param taskPermissions Mapping from task name to collection of permitted operations.
     * @param cachePermissions Mapping from cache name to collection of allowed operations.
     */
    public GridSecurityPermissionSetBean(boolean dfltAllowAll,
        Map<String, Collection<GridSecurityPermission>> taskPermissions,
        Map<String, Collection<GridSecurityPermission>> cachePermissions) {
        this.dfltAllowAll = dfltAllowAll;
        this.taskPermissions = taskPermissions;
        this.cachePermissions = cachePermissions;
    }

    /**
     * Gets default allow all flag.
     *
     * @return Default allow all flag.
     */
    @Override public boolean defaultAllowAll() {
        return dfltAllowAll;
    }

    /**
     * Sets default allow all flag.
     *
     * @param dfltAllowAll Default allow all flag.
     */
    public void defaultAllowAll(boolean dfltAllowAll) {
        this.dfltAllowAll = dfltAllowAll;
    }

    /**
     * Gets mapping from task name mask to allowed operations.
     *
     * @return Mapping from task name to collection of permitted operations.
     */
    @Override public Map<String, Collection<GridSecurityPermission>> taskPermissions() {
        return taskPermissions;
    }

    /**
     * Sets mapping from task name mask to allowed operations.
     *
     * @param taskPermissions Mapping from task name to collection of permitted operations.
     */
    public void taskPermissions(Map<String, Collection<GridSecurityPermission>> taskPermissions) {
        this.taskPermissions = taskPermissions;
    }

    /**
     * Gets mapping from cache name mask to collection of allowed operations.
     *
     * @return Mapping from cache name to collection of permitted operations.
     */
    @Override public Map<String, Collection<GridSecurityPermission>> cachePermissions() {
        return cachePermissions;
    }

    /**
     * Sets mapping from cache name mask to collection of allowed operations.
     *
     * @param cachePermissions Mapping from cache name to collection of allowed operations.
     */
    public void cachePermissions(Map<String, Collection<GridSecurityPermission>> cachePermissions) {
        this.cachePermissions = cachePermissions;
    }
}
