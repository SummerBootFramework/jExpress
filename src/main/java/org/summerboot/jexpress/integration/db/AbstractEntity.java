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
package org.summerboot.jexpress.integration.db;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/**
 *
 * @author Changski Tie Zheng Zhang 张铁铮, 魏泽北, 杜旺财, 杜富贵
 */
@MappedSuperclass
public abstract class AbstractEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Version
    @Column(name = "version")
    protected int version;

    @Column(name = "createdTs", nullable = false, updatable = false)
    protected OffsetDateTime createdTs = OffsetDateTime.now();

    @Column(name = "updatedTs", nullable = false, updatable = true)
    protected OffsetDateTime updatedTs = OffsetDateTime.now();

    protected AbstractEntity() {
    }

    @PreUpdate
    protected void updateTimestamp() {
        updatedTs = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Get the value of version
     *
     * @return the value of version
     */
    public int getVersion() {
        return version;
    }

    /**
     * Set the value of version
     *
     * @param version new value of version
     */
    public void setVersion(int version) {
        this.version = version;
    }

    public OffsetDateTime getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(OffsetDateTime createdTs) {
        this.createdTs = createdTs;
    }

    public OffsetDateTime getUpdatedTs() {
        return updatedTs;
    }

    public void setUpdatedTs(OffsetDateTime updatedTs) {
        this.updatedTs = updatedTs;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.id);
        hash = 97 * hash + this.version;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AbstractEntity other = (AbstractEntity) obj;
        if (this.version != other.version) {
            return false;
        }
        return Objects.equals(this.id, other.id);
    }

}
