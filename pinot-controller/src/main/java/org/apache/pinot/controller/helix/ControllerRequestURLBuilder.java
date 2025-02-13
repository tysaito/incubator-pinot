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
package org.apache.pinot.controller.helix;

import org.apache.avro.reflect.Nullable;
import org.apache.commons.lang.StringUtils;
import org.apache.pinot.common.utils.StringUtil;
import org.apache.pinot.common.utils.URIUtils;


/**
 * Sep 30, 2014
 */

public class ControllerRequestURLBuilder {
  private final String _baseUrl;
  private static final String TABLES = "tables";
  private static final String TENANTS = "tenants";

  private ControllerRequestURLBuilder(String baseUrl) {
    _baseUrl = baseUrl;
  }

  public static ControllerRequestURLBuilder baseUrl(String baseUrl) {
    return new ControllerRequestURLBuilder(baseUrl);
  }

  public String forResourceCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "dataresources");
  }

  public String forResourceDelete(String resourceName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "dataresources", resourceName);
  }

  public String forResourceGet(String resourceName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "dataresources", resourceName);
  }

  public String forDataFileUpload() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "segments");
  }

  public String forInstanceCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "instances/");
  }

  public String forInstanceDelete(String instanceName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "instances", instanceName);
  }

  public String forInstanceState(String instanceName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "instances", instanceName, "state");
  }

  public String forInstanceInformation(String instanceName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "instances", instanceName);
  }

  public String forInstanceList() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "instances");
  }

  public String forTablesFromTenant(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), TENANTS, tenantName, TABLES);
  }

  public String forInstanceBulkCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "instances", "bulkAdd");
  }

  // V2 API started
  public String forTenantCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants/");
  }

  public String forBrokerTenantCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants?type=broker");
  }

  public String forServerTenantCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants?type=server");
  }

  public String forTenantCreate(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants", tenantName, "instances");
  }

  public String forServerTenantCreate(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants", tenantName, "instances?type=server");
  }

  public String forBrokerTenantCreate(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants", tenantName, "instances?type=broker");
  }

  public String forTenantGet() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants");
  }

  public String forTenantGet(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants", tenantName);
  }

  public String forBrokerTenantGet(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants", tenantName, "?type=broker");
  }

  public String forServerTenantGet(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants", tenantName, "?type=server");
  }

  public String forBrokerTenantDelete(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants", tenantName, "?type=broker");
  }

  public String forServerTenantDelete(String tenantName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tenants", tenantName, "?type=server");
  }

  public String forTableCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables");
  }

  public String forNewTableCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "v2", "tables");
  }

  public String forUpdateTableConfig(String tableName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName);
  }

  public String forNewUpdateTableConfig(String tableName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "v2", "tables", tableName);
  }

  public String forTableRebalance(String tableName, String tableType) {
    String query = "rebalance?dryrun=false&type=" + tableType;
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName, query);
  }

  public String forTableReload(String tableName, String tableType) {
    String query = "reload?type=" + tableType;
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName, "segments", query);
  }

  public String forTableUpdateIndexingConfigs(String tableName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName, "indexingConfigs");
  }

  public String forTableGetServerInstances(String tableName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName, "instances?type=server");
  }

  public String forTableGetBrokerInstances(String tableName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName, "instances?type=broker");
  }

  public String forTableGet(String tableName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName);
  }

  public String forTableDelete(String tableName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName);
  }

  public String forTableView(String tableName, String view, @Nullable String tableType) {
    String url = StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName, view);
    if (tableType != null) {
      url += "?tableType=" + tableType;
    }
    return url;
  }

  public String forSchemaCreate() {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "schemas");
  }

  public String forSchemaUpdate(String schemaName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "schemas", schemaName);
  }

  public String forSchemaGet(String schemaName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "schemas", schemaName);
  }

  public static void main(String[] args) {
    System.out.println(ControllerRequestURLBuilder.baseUrl("localhost:8089").forResourceCreate());
    System.out.println(ControllerRequestURLBuilder.baseUrl("localhost:8089").forInstanceCreate());
  }

  public String forSegmentDownload(String tableName, String segmentName) {
    return URIUtils.constructDownloadUrl(StringUtils.chomp(_baseUrl, "/"), tableName, segmentName);
  }

  public String forSegmentDelete(String resourceName, String segmentName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "datafiles", resourceName, segmentName);
  }

  public String forSegmentDeleteAPI(String tableName, String segmentName, String tableType) {
    return URIUtils.getPath(StringUtils.chomp(_baseUrl, "/"), "segments", tableName, URIUtils.encode(segmentName))
        + "?type=" + tableType;
  }

  public String forSegmentDeleteAllAPI(String tableName, String tableType)
      throws Exception {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "segments", tableName + "?type=" + tableType);
  }

  public String forListAllSegments(String tableName)
      throws Exception {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName, "segments");
  }

  public String forListAllCrcInformationForTable(String tableName)
      throws Exception {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName, "segments", "crc");
  }

  public String forDeleteTableWithType(String tableName, String tableType)
      throws Exception {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName + "?type=" + tableType);
  }

  public String forDeleteSegmentWithGetAPI(String tableName, String segmentName, String tableType)
      throws Exception {
    return URIUtils
        .getPath(StringUtils.chomp(_baseUrl, "/"), "tables", tableName, "segments", URIUtils.encode(segmentName))
        + "?state=drop&type=" + tableType;
  }

  public String forDeleteAllSegmentsWithTypeWithGetAPI(String tableName, String tableType) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "tables", tableName,
        "segments" + "?state=drop&" + "type=" + tableType);
  }

  public String forSegmentListAPIWithTableType(String tableName, String tableType) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "segments", tableName + "?type=" + tableType);
  }

  public String forSegmentListAPI(String tableName) {
    return StringUtil.join("/", StringUtils.chomp(_baseUrl, "/"), "segments", tableName);
  }
}
