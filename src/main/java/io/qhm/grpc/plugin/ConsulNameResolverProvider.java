package io.qhm.grpc.plugin;

import com.google.common.base.Preconditions;

import io.grpc.Attributes;
import io.grpc.NameResolverProvider;
import io.grpc.internal.GrpcUtil;

import java.net.URI;

/**
 * A provider for {@link ConsulNameResolver}.
 *
 * <p>It resolves a target URI whose scheme is {@code "consul"}. The (optional) authority of the target
 * URI is reserved for the address of alternative consul server (not implemented yet). The path of the
 * target URI, excluding the leading slash {@code '/'}, is treated as the host name and the optional
 * port to be resolved by consul. Example target URIs:
 *
 * <ul>
 *   <li>{@code "consul:///foo.googleapis.com:8080"} (using default consul)</li>
 *   yet))</li>
 *   <li>{@code "consul:///foo.googleapis.com"} (without port)</li>
 * </ul>
 */
public final class ConsulNameResolverProvider extends NameResolverProvider {

  private static final String SCHEME = "consul";

  @Override
  public ConsulNameResolver newNameResolver(URI targetUri, Attributes params) {
    if (SCHEME.equals(targetUri.getScheme())) {
      String targetPath = Preconditions.checkNotNull(targetUri.getPath(), "targetPath");
      Preconditions.checkArgument(targetPath.startsWith("/"),
          "the path component (%s) of the target (%s) must start with '/'", targetPath, targetUri);
      String name = targetPath.substring(1);
      return new ConsulNameResolver(targetUri.getAuthority(), name, params, GrpcUtil.TIMER_SERVICE,
          GrpcUtil.SHARED_CHANNEL_EXECUTOR);
    } else {
      return null;
    }
  }

  @Override
  public String getDefaultScheme() {
    return SCHEME;
  }

  @Override
  protected boolean isAvailable() {
    return true;
  }

  @Override
  protected int priority() {
    return 5;
  }
}
