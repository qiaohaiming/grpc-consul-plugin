This is a plugin for grpc find service based on consul.
example:
    ManagedChannel channel = ManagedChannelBuilder.forTarget("consul:///helloserver:50051")
			.nameResolverFactory(new ConsulNameResolverProvider())
			.loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
			.usePlaintext(true)
			.build();
    blockingStub = GreeterGrpc.newBlockingStub(channel);