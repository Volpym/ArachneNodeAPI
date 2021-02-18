/*
 *
 * Copyright 2018 Odysseus Data Services, inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Company: Odysseus Data Services, Inc.
 * Product Owner/Architecture: Gregory Klebanov
 * Authors: Pavel Grafkin, Alexandr Ryabokon, Vitaly Koulakov, Anton Gackovka, Maria Pozhidaeva, Mikhail Mironov
 * Created: December 19, 2016
 *
 */

package com.odysseusinc.arachne.datanode.service.impl;

import com.odysseusinc.arachne.commons.api.v1.dto.CommonHealthStatus;
import com.odysseusinc.arachne.datanode.exception.AlreadyExistsException;
import com.odysseusinc.arachne.datanode.model.datanode.DataNode;
import com.odysseusinc.arachne.datanode.model.datanode.FunctionalMode;
import com.odysseusinc.arachne.datanode.model.user.User;
import com.odysseusinc.arachne.datanode.repository.DataNodeRepository;
import com.odysseusinc.arachne.datanode.service.BaseCentralIntegrationService;
import com.odysseusinc.arachne.datanode.service.DataNodeService;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DataNodeServiceImpl implements DataNodeService {

    private static final String ALREADY_EXISTS_EXCEPTION = "DataNode entry already exist, try to update it";

    private final DataNodeRepository dataNodeRepository;
    private final BaseCentralIntegrationService centralIntegrationService;

    private final FunctionalMode mode;

    @Autowired
    public DataNodeServiceImpl(@Lazy BaseCentralIntegrationService centralIntegrationService,
                               DataNodeRepository dataNodeRepository,
                               @Value("${datanode.runMode}") String runMode) {

        this.centralIntegrationService = centralIntegrationService;
        this.dataNodeRepository = dataNodeRepository;
        this.mode = FunctionalMode.valueOf(runMode);
    }

    @Override
    public Optional<DataNode> findCurrentDataNode() {

        List<DataNode> dataNodes = dataNodeRepository.findAll();
        if (dataNodes == null || dataNodes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(dataNodes.get(0));
    }

    @Override
    public DataNode findCurrentDataNodeOrCreate(User user) {

        Optional<DataNode> currentDataNode = findCurrentDataNode();
        return currentDataNode.orElseGet(() -> currentDataNode.orElse(create(user, new DataNode())));
    }

    @Override
    public DataNode create(User user, DataNode dataNode) throws AlreadyExistsException {

        final Optional<DataNode> currentDataNode = findCurrentDataNode();
        if (currentDataNode.isPresent()) {
            throw new AlreadyExistsException(ALREADY_EXISTS_EXCEPTION);
        }
        if (getDataNodeMode() != FunctionalMode.STANDALONE) {
            dataNode = centralIntegrationService.sendDataNodeCreationRequest(user, dataNode);
        }
        return dataNodeRepository.save(dataNode);
    }


    @Override
    public void updateHealthStatus(Long id, CommonHealthStatus healthStatus, String healthStatusDescription) {

        dataNodeRepository.updateHealthStatus(id, healthStatus, healthStatusDescription);
    }

    @Override
    public FunctionalMode getDataNodeMode() {

        return mode;
    }

    @Override
    public boolean isNetworkMode() {

        return FunctionalMode.NETWORK == mode;
    }
}
