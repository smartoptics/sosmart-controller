/*
 * Copyright © 2017 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.networkmodel;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.transportpce.networkmodel.service.NetworkModelService;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.deletenode.rev230809.DeleteRoadmNodeInput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.deletenode.rev230809.DeleteRoadmNodeOutput;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.deletenode.rev230809.DeleteRoadmNodeOutputBuilder;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.deletenode.rev230809.TransportpceDeletenodeService;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteNodeImpl implements TransportpceDeletenodeService {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteNodeImpl.class);
    private final NetworkModelService networkModelService;

    public DeleteNodeImpl(final NetworkModelService networkModelService) {
        this.networkModelService = networkModelService;
    }

    @Override
    public ListenableFuture<RpcResult<DeleteRoadmNodeOutput>> deleteRoadmNode(DeleteRoadmNodeInput input) {
        LOG.info("RPC Delete Roadm Node called for {}", input.getNodeId());
        boolean success = this.networkModelService.deleteOpenRoadmnode(input.getNodeId());
        if (!success) {
            return RpcResultBuilder.<DeleteRoadmNodeOutput>failed()
                .withError(ErrorType.RPC, "Error when deleting node")
                .buildFuture();
        }
        return RpcResultBuilder
            .success(new DeleteRoadmNodeOutputBuilder()
                .setResult("Roadm node deleted successfully")
                .build())
            .buildFuture();
    }
}