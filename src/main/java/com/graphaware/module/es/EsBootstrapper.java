/*
 * Copyright (c) 2014 GraphAware
 *
 * This file is part of GraphAware.
 *
 * GraphAware is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.graphaware.module.es;

import com.graphaware.module.es.util.CustomClassLoading;
import com.graphaware.module.es.util.PassThroughProxyHandler;
import com.graphaware.module.es.wrapper.ESClientWrapper;
import com.graphaware.module.es.wrapper.IGenericClientWrapper;
import com.graphaware.runtime.module.RuntimeModule;
import com.graphaware.runtime.module.RuntimeModuleBootstrapper;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

/**
 * Bootstraps the {@link EsModule} in server mode.
 */
public class EsBootstrapper implements RuntimeModuleBootstrapper
{

  private static final Logger LOG = LoggerFactory.getLogger(EsBootstrapper.class);

  //keys to use when configuring using neo4j.properties
  private static final String ES_CLASSPATH = "classpath";
  private static final String ES_INDEXNAME = "indexName";
  private static final String ES_CLUSTER_NODES = "cluserNodes";
  private static final String ES_CLUSTER_HOSTNAME = "cluserNodeHost";
  private static final String ES_CLUSTER_PORT = "cluserNodePort";

  /**
   * @{inheritDoc}
   */
  @Override
  public RuntimeModule bootstrapModule(String moduleId, Map<String, String> config, GraphDatabaseService database)
  {
    EsConfiguration configuration = EsConfiguration.defaultConfiguration();

    if (config.get(ES_CLASSPATH) != null && config.get(ES_CLASSPATH).length() > 0)
    {
      configuration = configuration.withClasspathDirectoryProperty(config.get(ES_CLASSPATH));
      LOG.info("classpath set to {}", configuration.getClasspathDirectory());
    }

    if (config.get(ES_INDEXNAME) != null && config.get(ES_INDEXNAME).length() > 0)
    {
      configuration = configuration.withIndexName(config.get(ES_INDEXNAME));
      LOG.info("indexName set to {}", configuration.getIndexName());
    }
    
    if (config.get(ES_CLUSTER_NODES) != null && config.get(ES_CLUSTER_NODES).length() > 0)
    {
      int nodes = Integer.parseInt(config.get(ES_CLUSTER_NODES));
      List<InetSocketAddress> addresses = new ArrayList<>();
      for (int i = 0; i < nodes; i++)
      {
        String host = config.get(ES_CLUSTER_HOSTNAME + "." + i);
        String port = config.get(ES_CLUSTER_PORT + "." + i);
        LOG.info("ClusterNodes set to {} {}", host, port);
        addresses.add(new InetSocketAddress(host, Integer.valueOf(port)));
      }
      configuration = configuration.withClusterAddresses(addresses);
      
    }
    IGenericClientWrapper indexWrapper = createWrapperCustomClassLoader(configuration);
    return new EsModule(moduleId, configuration, database, indexWrapper);
  }
  
  private IGenericClientWrapper createWrapperCustomClassLoader(EsConfiguration configuration) throws RuntimeException
  {
    IGenericClientWrapper indexWrapper = null;
    try
    {
      CustomClassLoading loader = new CustomClassLoading(configuration.getClasspathDirectory());
      Class<Object> loadedClass = (Class<Object>) loader.loadClass("com.graphaware.module.es.wrapper.ESClientWrapper");
      indexWrapper = (IGenericClientWrapper) Proxy.newProxyInstance(this.getClass().getClassLoader(),
              new Class[] { IGenericClientWrapper.class },
              new PassThroughProxyHandler(loadedClass.newInstance()));
      indexWrapper.startClient(configuration.getClusterName(), configuration.getClusterAddresses());
    }
    catch (Exception ex)
    {
      LOG.error("Error while starting el client node", ex);
      throw new RuntimeException("Error while starting ElasticSearch client wrapper", ex);
    }
    return indexWrapper;
  }
}
