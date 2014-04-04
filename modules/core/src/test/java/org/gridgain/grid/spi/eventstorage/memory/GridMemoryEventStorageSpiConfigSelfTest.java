/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.eventstorage.memory;

import org.gridgain.testframework.junits.spi.*;

/**
 * Memory event storage SPI config test.
 */
@GridSpiTest(spi = GridMemoryEventStorageSpi.class, group = "Event Storage SPI")
public class GridMemoryEventStorageSpiConfigSelfTest extends GridSpiAbstractConfigTest<GridMemoryEventStorageSpi> {
    /**
     * @throws Exception If failed.
     */
    public void testNegativeConfig() throws Exception {
        checkNegativeSpiProperty(new GridMemoryEventStorageSpi(), "expireCount", 0);
        checkNegativeSpiProperty(new GridMemoryEventStorageSpi(), "expireAgeMs", 0);
    }
}