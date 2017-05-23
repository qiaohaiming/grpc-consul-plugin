package io.qhm.grpc.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.orbitz.consul.CatalogClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.catalog.CatalogService;

import io.grpc.Attributes;
import io.grpc.NameResolver;
import io.grpc.ResolvedServerInfo;
import io.grpc.Status;
import io.grpc.internal.LogExceptionRunnable;
import io.grpc.internal.SharedResourceHolder;
import io.grpc.internal.SharedResourceHolder.Resource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consul-based {@link NameResolver}.
 *
 * @see ConsulNameResolverFactory
 */
class ConsulNameResolver extends NameResolver {
  private static Logger LOG = LoggerFactory.getLogger(ConsulNameResolver.class);
  private final String authority;
  private final String host;
  private final int port;
  private final Resource<ScheduledExecutorService> timerServiceResource;
  private final Resource<ExecutorService> executorResource;
  @GuardedBy("this")
  private boolean shutdown;
  @GuardedBy("this")
  private ScheduledExecutorService timerService;
  @GuardedBy("this")
  private ExecutorService executor;
  @GuardedBy("this")
  private ScheduledFuture<?> resolutionTask;
  @GuardedBy("this")
  private boolean resolving;
  @GuardedBy("this")
  private Listener listener;
  @GuardedBy("this")
  private Consul consul;
  @GuardedBy("this")
  private CatalogClient catalogClient;


  ConsulNameResolver(@Nullable String nsAuthority, String name, Attributes params,
      Resource<ScheduledExecutorService> timerServiceResource,
      Resource<ExecutorService> executorResource) {
    // TODO: if a Consul server is provided as nsAuthority, use it.

    this.timerServiceResource = timerServiceResource;
    this.executorResource = executorResource;
    // Must prepend a "//" to the name when constructing a URI, otherwise it will be treated as an
    // opaque URI, thus the authority and host of the resulted URI would be null.
    URI nameUri = URI.create("//" + name);
    authority = Preconditions.checkNotNull(nameUri.getAuthority(),
        "nameUri (%s) doesn't have an authority", nameUri);
    host = Preconditions.checkNotNull(nameUri.getHost(), "host");

    port = nameUri.getPort();
	String url = ConfigUtil.getConfig("consul_http_url");
	LOG.info("connect to consul:{}...", url);

	consul = Consul.builder().withUrl(url).build();

    catalogClient = consul.catalogClient();
 
  }

  @Override
  public final String getServiceAuthority() {
    return authority;
  }

  @Override
  public final synchronized void start(Listener listener) {
    Preconditions.checkState(this.listener == null, "already started");
    timerService = SharedResourceHolder.get(timerServiceResource);
    executor = SharedResourceHolder.get(executorResource);
    this.listener = Preconditions.checkNotNull(listener, "listener");
    resolve();
  }

  @Override
  public final synchronized void refresh() {
    Preconditions.checkState(listener != null, "not started");
    resolve();
  }

  private final Runnable resolutionRunnable = new Runnable() {
      public void run() {
    	List<CatalogService>  cataloglist;
        Listener savedListener;
        synchronized (ConsulNameResolver.this) {
          // If this task is started by refresh(), there might already be a scheduled task.
          if (resolutionTask != null) {
            resolutionTask.cancel(false);
            resolutionTask = null;
          }
          if (shutdown) {
            return;
          }
          savedListener = listener;
          resolving = true;
        }
        try {
          try {
        	  cataloglist = getAllByName(host);
          } catch (UnknownHostException e) {
            synchronized (ConsulNameResolver.this) {
              if (shutdown) {
                return;
              }
              // Because timerService is the single-threaded GrpcUtil.TIMER_SERVICE in production,
              // we need to delegate the blocking work to the executor
              resolutionTask =
                  timerService.schedule(new LogExceptionRunnable(resolutionRunnableOnExecutor),
                      1, TimeUnit.MINUTES);
            }
            savedListener.onError(Status.UNAVAILABLE.withCause(e));
            return;
          }
          List<ResolvedServerInfo> servers =
              new ArrayList<ResolvedServerInfo>(cataloglist.size());
          for (int i = 0; i < cataloglist.size(); i++) {
            servers.add(
                new ResolvedServerInfo(new InetSocketAddress(cataloglist.get(i).getAddress(), port>0?port:cataloglist.get(i).getServicePort()), Attributes.EMPTY));
          }
          Collections.shuffle(servers);
          savedListener.onUpdate(
              Collections.singletonList(servers), Attributes.EMPTY);
        } finally {
          synchronized (ConsulNameResolver.this) {
            resolving = false;
          }
        }
      }
    };

  private final Runnable resolutionRunnableOnExecutor = new Runnable() {
      public void run() {
        synchronized (ConsulNameResolver.this) {
          if (!shutdown) {
            executor.execute(resolutionRunnable);
          }
        }
      }
    };

  // To be mocked out in tests
  @VisibleForTesting
  List<CatalogService> getAllByName(String host) throws UnknownHostException {
	  List<CatalogService> response = catalogClient.getService(host).getResponse();
	  if(response.isEmpty()){
		  throw new UnknownHostException("no such service["+host+"] in consul");
	  }
	return response;
  }

  @GuardedBy("this")
  private void resolve() {
    if (resolving || shutdown) {
      return;
    }
    executor.execute(resolutionRunnable);
  }

  @Override
  public final synchronized void shutdown() {
    if (shutdown) {
      return;
    }
    shutdown = true;
    if (resolutionTask != null) {
      resolutionTask.cancel(false);
    }
    if (timerService != null) {
      timerService = SharedResourceHolder.release(timerServiceResource, timerService);
    }
    if (executor != null) {
      executor = SharedResourceHolder.release(executorResource, executor);
    }
  }

  final int getPort() {
    return port;
  }
}
