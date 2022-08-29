/*
 * Copyright © 2020 Orange Labs, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.pce.service;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;
import org.opendaylight.transportpce.common.network.NetworkTransactionService;
import org.opendaylight.transportpce.test.AbstractTest;

public class PathComputationServiceImplTest extends AbstractTest {

    private PathComputationServiceImpl pathComputationServiceImpl;
    private static NetworkTransactionService networkTransactionService = null;

    @Before
    public void setUp() {
        networkTransactionService = Mockito.mock(NetworkTransactionService.class);
        pathComputationServiceImpl = new PathComputationServiceImpl(
                networkTransactionService,
                this.getNotificationPublishService(), null);
        pathComputationServiceImpl.init();
    }

    @After
    public void destroy() {
        pathComputationServiceImpl.close();
    }
}
