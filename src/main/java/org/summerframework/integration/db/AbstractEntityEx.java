/*
 * Copyright 2005-2022 Du Law Office - The Summer Boot Framework Project
 *
 * The Summer Boot Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License and you have no
 * policy prohibiting employee contributions back to this file (unless the contributor to this
 * file is your current or retired employee). You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.summerframework.integration.db;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@MappedSuperclass
public class AbstractEntityEx extends AbstractEntity {

    @Column(name = "createdBy", nullable = true, updatable = false)
    protected String createdBy;

    @Column(name = "createdIP", nullable = true, updatable = false)
    protected String createdIP;

    @Column(name = "updatedBy", nullable = true, updatable = false)
    protected String updatedBy;

    @Column(name = "updatedIP", nullable = true, updatable = false)
    protected String updatedIP;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedIP() {
        return createdIP;
    }

    public void setCreatedIP(String createdIP) {
        this.createdIP = createdIP;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedIP() {
        return updatedIP;
    }

    public void setUpdatedIP(String updatedIP) {
        this.updatedIP = updatedIP;
    }

}
