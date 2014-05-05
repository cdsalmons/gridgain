// @java.file.header

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

/**
 * Component type.
 */
public enum GridComponentType {
    /** GGFS. */
    GGFS("org.gridgain.grid.kernal.processors.ggfs.GridGgfsOpProcessor"),
    /** Spring. */
    SPRING("org.gridgain.grid.kernal.processors.config.spring.GridSpringConfigurationProcessor"),
    /** H2 indexing SPI. */
    H2_INDEXING("org.gridgain.grid.spi.indexing.h2.GridH2IndexingSpi");

    /** Class name. */
    private final String clsName;

    /**
     * Constructor.
     *
     * @param clsName Class name.
     */
    GridComponentType(String clsName) {
        this.clsName = clsName;
    }

    /**
     * @return Component class name.
     */
    public String className() {
        return clsName;
    }
}