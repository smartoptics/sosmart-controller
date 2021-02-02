/*
 * Copyright © 2021 Orange, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.dmaap.client.impl;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.transportpce.dmaap.client.listener.NbiNotificationsListenerImpl;


public class DmaapClientProviderTest {

    @Mock
    private NotificationService notificationService;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);

    }

    @Test
    public void testInitRegisterNbiNotificationsToRpcRegistry() {
        DmaapClientProvider provider =  new DmaapClientProvider(notificationService, "http://localhost", "username", "password");
        provider.init();
        (verify(notificationService, times(1)))
                .registerNotificationListener(Mockito.any(NbiNotificationsListenerImpl.class));
    }

}
