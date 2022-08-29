/*
 * Copyright © 2017 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.networkmodel;

import static org.opendaylight.transportpce.common.StringConstants.OPENROADM_DEVICE_VERSION_2_2_1;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.transportpce.networkmodel.service.NetworkModelService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.createnode.rev221006.CreateRoadmNodeInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.createnode.rev221006.CreateRoadmNodeOutput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.createnode.rev221006.CreateRoadmNodeOutputBuilder;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.createnode.rev221006.TransportpceCreatenodeService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateNodeImpl implements TransportpceCreatenodeService {

    private static final Logger LOG = LoggerFactory.getLogger(CreateNodeImpl.class);
    private final NetworkModelService networkModelService;

    public CreateNodeImpl(final NetworkModelService networkModelService) {
        this.networkModelService = networkModelService;
    }

    @Override
    public ListenableFuture<RpcResult<CreateRoadmNodeOutput>> createRoadmNode(CreateRoadmNodeInput input) {
        LOG.info("RPC Create Roadm Node called for {}", input.getNodeId());
        this.networkModelService
            .createOpenRoadmNode(input.getNodeId(), OPENROADM_DEVICE_VERSION_2_2_1, false);
        return RpcResultBuilder
            .success(new CreateRoadmNodeOutputBuilder()
                .setResult("Roadm node created successfully")
                .build())
            .buildFuture();
    }
}