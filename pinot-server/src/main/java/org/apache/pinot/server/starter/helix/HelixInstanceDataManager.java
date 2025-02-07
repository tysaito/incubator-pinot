/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.server.starter.helix;

import com.google.common.base.Preconditions;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.apache.helix.ZNRecord;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
import org.apache.pinot.common.config.TableConfig;
import org.apache.pinot.common.config.TableNameBuilder;
import org.apache.pinot.common.data.Schema;
import org.apache.pinot.common.metadata.ZKMetadataProvider;
import org.apache.pinot.common.metrics.ServerMetrics;
import org.apache.pinot.common.segment.SegmentMetadata;
import org.apache.pinot.common.utils.CommonConstants;
import org.apache.pinot.core.data.manager.InstanceDataManager;
import org.apache.pinot.core.data.manager.SegmentDataManager;
import org.apache.pinot.core.data.manager.TableDataManager;
import org.apache.pinot.core.data.manager.config.TableDataManagerConfig;
import org.apache.pinot.core.data.manager.offline.TableDataManagerProvider;
import org.apache.pinot.core.indexsegment.immutable.ImmutableSegment;
import org.apache.pinot.core.indexsegment.immutable.ImmutableSegmentLoader;
import org.apache.pinot.core.segment.index.loader.IndexLoadingConfig;
import org.apache.pinot.core.segment.index.loader.LoaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The class <code>HelixInstanceDataManager</code> is the instance data manager based on Helix.
 *
 * TODO: move SegmentFetcherAndLoader into this class to make this the top level manager
 */
@ThreadSafe
public class HelixInstanceDataManager implements InstanceDataManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(HelixInstanceDataManager.class);

  private final ConcurrentHashMap<String, TableDataManager> _tableDataManagerMap = new ConcurrentHashMap<>();

  private HelixInstanceDataManagerConfig _instanceDataManagerConfig;
  private String _instanceId;
  private ZkHelixPropertyStore<ZNRecord> _propertyStore;
  private ServerMetrics _serverMetrics;

  @Override
  public synchronized void init(@Nonnull Configuration config, @Nonnull ZkHelixPropertyStore<ZNRecord> propertyStore,
      @Nonnull ServerMetrics serverMetrics)
      throws ConfigurationException {
    LOGGER.info("Initializing Helix instance data manager");

    _instanceDataManagerConfig = new HelixInstanceDataManagerConfig(config);
    LOGGER.info("HelixInstanceDataManagerConfig: {}", _instanceDataManagerConfig);
    _instanceId = _instanceDataManagerConfig.getInstanceId();
    _propertyStore = propertyStore;
    _serverMetrics = serverMetrics;

    File instanceDataDir = new File(_instanceDataManagerConfig.getInstanceDataDir());
    if (!instanceDataDir.exists()) {
      Preconditions.checkState(instanceDataDir.mkdirs());
    }
    File instanceSegmentTarDir = new File(_instanceDataManagerConfig.getInstanceSegmentTarDir());
    if (!instanceSegmentTarDir.exists()) {
      Preconditions.checkState(instanceSegmentTarDir.mkdirs());
    }

    // Initialize the table data manager provider
    TableDataManagerProvider.init(_instanceDataManagerConfig);

    LOGGER.info("Initialized Helix instance data manager");
  }

  @Override
  public synchronized void start() {
    LOGGER.info("Helix instance data manager started");
  }

  @Override
  public synchronized void shutDown() {
    LOGGER.info("Helix instance data manager shut down");
  }

  @Override
  public void addOfflineSegment(@Nonnull String offlineTableName, @Nonnull String segmentName, @Nonnull File indexDir)
      throws Exception {
    LOGGER.info("Adding segment: {} to table: {}", segmentName, offlineTableName);
    TableConfig tableConfig = ZKMetadataProvider.getTableConfig(_propertyStore, offlineTableName);
    Preconditions.checkNotNull(tableConfig);
    _tableDataManagerMap.computeIfAbsent(offlineTableName, k -> createTableDataManager(k, tableConfig))
        .addSegment(indexDir, new IndexLoadingConfig(_instanceDataManagerConfig, tableConfig));
    LOGGER.info("Added segment: {} to table: {}", segmentName, offlineTableName);
  }

  @Override
  public void addRealtimeSegment(@Nonnull String realtimeTableName, @Nonnull String segmentName)
      throws Exception {
    LOGGER.info("Adding segment: {} to table: {}", segmentName, realtimeTableName);
    TableConfig tableConfig = ZKMetadataProvider.getTableConfig(_propertyStore, realtimeTableName);
    Preconditions.checkNotNull(tableConfig);
    _tableDataManagerMap.computeIfAbsent(realtimeTableName, k -> createTableDataManager(k, tableConfig))
        .addSegment(segmentName, tableConfig, new IndexLoadingConfig(_instanceDataManagerConfig, tableConfig));
    LOGGER.info("Added segment: {} to table: {}", segmentName, realtimeTableName);
  }

  private TableDataManager createTableDataManager(@Nonnull String tableNameWithType, @Nonnull TableConfig tableConfig) {
    LOGGER.info("Creating table data manager for table: {}", tableNameWithType);
    TableDataManagerConfig tableDataManagerConfig =
        TableDataManagerConfig.getDefaultHelixTableDataManagerConfig(_instanceDataManagerConfig, tableNameWithType);
    tableDataManagerConfig.overrideConfigs(tableConfig);
    TableDataManager tableDataManager = TableDataManagerProvider
        .getTableDataManager(tableDataManagerConfig, _instanceId, _propertyStore, _serverMetrics);
    tableDataManager.start();
    LOGGER.info("Created table data manager for table: {}", tableNameWithType);
    return tableDataManager;
  }

  @Override
  public void removeSegment(@Nonnull String tableNameWithType, @Nonnull String segmentName) {
    LOGGER.info("Removing segment: {} from table: {}", segmentName, tableNameWithType);
    TableDataManager tableDataManager = _tableDataManagerMap.get(tableNameWithType);
    if (tableDataManager != null) {
      tableDataManager.removeSegment(segmentName);
      LOGGER.info("Removed segment: {} from table: {}", segmentName, tableNameWithType);
    }
  }

  @Override
  public void notifySegmentAdded(@Nonnull String tableNameWithType, @Nonnull String segmentName) {
    TableDataManager tableDataManager = _tableDataManagerMap.get(tableNameWithType);
    if (tableDataManager != null) {
      tableDataManager.notifySegmentAdded(segmentName);
    }
  }

  @Override
  public void notifySegmentDeleted(@Nonnull String tableNameWithType, @Nonnull String segmentName) {
    TableDataManager tableDataManager = _tableDataManagerMap.get(tableNameWithType);
    if (tableDataManager != null) {
      tableDataManager.notifySegmentDeleted(segmentName);
    }
  }

  @Override
  public void reloadSegment(@Nonnull String tableNameWithType, @Nonnull String segmentName)
      throws Exception {
    LOGGER.info("Reloading single segment: {} in table: {}", segmentName, tableNameWithType);
    SegmentMetadata segmentMetadata = getSegmentMetadata(tableNameWithType, segmentName);
    if (segmentMetadata == null) {
      return;
    }

    TableConfig tableConfig = ZKMetadataProvider.getTableConfig(_propertyStore, tableNameWithType);
    Preconditions.checkNotNull(tableConfig);

    Schema schema = ZKMetadataProvider.getTableSchema(_propertyStore, tableNameWithType);

    reloadSegment(tableNameWithType, segmentMetadata, tableConfig, schema);

    LOGGER.info("Reloaded single segment: {} in table: {}", segmentName, tableNameWithType);
  }

  @Override
  public void reloadAllSegments(@Nonnull String tableNameWithType)
      throws Exception {
    LOGGER.info("Reloading all segments in table: {}", tableNameWithType);
    TableConfig tableConfig = ZKMetadataProvider.getTableConfig(_propertyStore, tableNameWithType);
    Preconditions.checkNotNull(tableConfig);

    Schema schema = ZKMetadataProvider.getTableSchema(_propertyStore, tableNameWithType);

    for (SegmentMetadata segmentMetadata : getAllSegmentsMetadata(tableNameWithType)) {
      reloadSegment(tableNameWithType, segmentMetadata, tableConfig, schema);
    }

    LOGGER.info("Reloaded all segments in table: {}", tableNameWithType);
  }

  private void reloadSegment(@Nonnull String tableNameWithType, @Nonnull SegmentMetadata segmentMetadata,
      @Nonnull TableConfig tableConfig, @Nullable Schema schema)
      throws Exception {
    String segmentName = segmentMetadata.getName();
    LOGGER.info("Reloading segment: {} in table: {}", segmentName, tableNameWithType);

    File indexDir = segmentMetadata.getIndexDir();
    if (indexDir == null) {
      LOGGER.info("Skip reloading REALTIME consuming segment: {} in table: {}", segmentName, tableNameWithType);
      return;
    }
    Preconditions.checkState(indexDir.isDirectory(), "Index directory: %s is not a directory", indexDir);

    File parentFile = indexDir.getParentFile();
    File segmentBackupDir =
        new File(parentFile, indexDir.getName() + CommonConstants.Segment.SEGMENT_BACKUP_DIR_SUFFIX);

    // This method might modify the file on disk. Use segment lock to prevent race condition
    Lock segmentLock = SegmentLocks.getSegmentLock(tableNameWithType, segmentName);
    try {
      segmentLock.lock();

      // First rename index directory to segment backup directory so that original segment have all file descriptors
      // point to the segment backup directory to ensure original segment serves queries properly

      // Rename index directory to segment backup directory (atomic)
      Preconditions.checkState(indexDir.renameTo(segmentBackupDir),
          "Failed to rename index directory: %s to segment backup directory: %s", indexDir, segmentBackupDir);

      // Copy from segment backup directory back to index directory
      FileUtils.copyDirectory(segmentBackupDir, indexDir);

      // Load from index directory
      ImmutableSegment immutableSegment = ImmutableSegmentLoader
          .load(indexDir, new IndexLoadingConfig(_instanceDataManagerConfig, tableConfig), schema);

      // Replace the old segment in memory
      _tableDataManagerMap.get(tableNameWithType).addSegment(immutableSegment);

      // Rename segment backup directory to segment temporary directory (atomic)
      // The reason to first rename then delete is that, renaming is an atomic operation, but deleting is not. When we
      // rename the segment backup directory to segment temporary directory, we know the reload already succeeded, so
      // that we can safely delete the segment temporary directory
      File segmentTempDir = new File(parentFile, indexDir.getName() + CommonConstants.Segment.SEGMENT_TEMP_DIR_SUFFIX);
      Preconditions.checkState(segmentBackupDir.renameTo(segmentTempDir),
          "Failed to rename segment backup directory: %s to segment temporary directory: %s", segmentBackupDir,
          segmentTempDir);
      LOGGER.info("Reloaded segment: {} in table: {}", segmentName, tableNameWithType);

      // Delete segment temporary directory
      FileUtils.deleteDirectory(segmentTempDir);
    } finally {
      LoaderUtils.reloadFailureRecovery(indexDir);
      segmentLock.unlock();
    }
  }

  @Nonnull
  @Override
  public Set<String> getAllTables() {
    return _tableDataManagerMap.keySet();
  }

  @Nullable
  @Override
  public TableDataManager getTableDataManager(@Nonnull String tableNameWithType) {
    return _tableDataManagerMap.get(tableNameWithType);
  }

  @Nonnull
  @Override
  public String getSegmentDataDirectory() {
    return _instanceDataManagerConfig.getInstanceDataDir();
  }

  @Nonnull
  @Override
  public String getSegmentFileDirectory() {
    return _instanceDataManagerConfig.getInstanceSegmentTarDir();
  }

  @Override
  public int getMaxParallelRefreshThreads() {
    return _instanceDataManagerConfig.getMaxParallelRefreshThreads();
  }

  @Nullable
  @Override
  public SegmentMetadata getSegmentMetadata(@Nonnull String tableNameWithType, @Nonnull String segmentName) {
    TableDataManager tableDataManager = _tableDataManagerMap.get(tableNameWithType);
    if (tableDataManager != null) {
      SegmentDataManager segmentDataManager = tableDataManager.acquireSegment(segmentName);
      if (segmentDataManager == null) {
        return null;
      }
      try {
        return segmentDataManager.getSegment().getSegmentMetadata();
      } finally {
        tableDataManager.releaseSegment(segmentDataManager);
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public List<SegmentMetadata> getAllSegmentsMetadata(@Nonnull String tableNameWithType) {
    TableDataManager tableDataManager = _tableDataManagerMap.get(tableNameWithType);
    if (tableDataManager == null) {
      return Collections.emptyList();
    } else {
      List<SegmentDataManager> segmentDataManagers = tableDataManager.acquireAllSegments();
      try {
        List<SegmentMetadata> segmentsMetadata = new ArrayList<>(segmentDataManagers.size());
        for (SegmentDataManager segmentDataManager : segmentDataManagers) {
          segmentsMetadata.add(segmentDataManager.getSegment().getSegmentMetadata());
        }
        return segmentsMetadata;
      } finally {
        for (SegmentDataManager segmentDataManager : segmentDataManagers) {
          tableDataManager.releaseSegment(segmentDataManager);
        }
      }
    }
  }
}
