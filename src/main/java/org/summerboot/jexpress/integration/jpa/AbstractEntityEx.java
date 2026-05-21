/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.summerboot.jexpress.integration.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@MappedSuperclass
public abstract class AbstractEntityEx extends AbstractEntity {

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
