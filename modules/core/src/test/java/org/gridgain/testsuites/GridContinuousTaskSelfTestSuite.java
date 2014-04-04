/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.testsuites;

import junit.framework.*;
import org.gridgain.grid.kernal.*;

/**
 * Continuous task self-test suite.
 */
public class GridContinuousTaskSelfTestSuite extends TestSuite {
    /**
     * @return Test suite.
     * @throws Exception If failed.
     */
    public static TestSuite suite() throws Exception {
        TestSuite suite = new TestSuite("Gridgain Kernal Test Suite");

        suite.addTest(new TestSuite(GridContinuousJobAnnotationSelfTest.class));
        suite.addTest(new TestSuite(GridContinuousJobSiblingsSelfTest.class));
        suite.addTest(new TestSuite(GridContinuousTaskSelfTest.class));
        suite.addTest(new TestSuite(GridTaskContinuousMapperSelfTest.class));

        return suite;
    }

}
