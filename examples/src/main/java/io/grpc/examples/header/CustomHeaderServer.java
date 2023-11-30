/*
 * Copyright 2015 The gRPC Authors
 *
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
 */

package io.grpc.examples.header;

import io.grpc.*;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.grpc.examples.header.HeaderServerInterceptor.InterceptServerCall;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A simple server that like {@link io.grpc.examples.helloworld.HelloWorldServer}.
 * You can get and response any header in {@link io.grpc.examples.header.HeaderServerInterceptor}.
 */
public class CustomHeaderServer {
    private static final Logger logger = Logger.getLogger(CustomHeaderServer.class.getName());

    /* The port on which the server should run */
    private static final int PORT = 50051;
    private Server server;

    private void start() throws IOException {
        server = ServerBuilder.forPort(PORT)
                .addService(new CustomGreeter())
                .build()
                .start();
        logger.info("Server started, listening on " + PORT);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    CustomHeaderServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final CustomHeaderServer server = new CustomHeaderServer();
        server.start();
        server.blockUntilShutdown();
    }

    private static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
        @Override
        public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

    private class CustomGreeter implements BindableService {
        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(GreeterGrpc.getServiceDescriptor())
                    .addMethod(
                            GreeterGrpc.getSayHelloMethod(),
                            new MiddleServerCallHandler<>(
                                    ServerCalls.asyncUnaryCall(
                                            (request, responseObserver) -> {
                                                HelloReply reply = HelloReply
                                                        .newBuilder()
                                                        .setMessage("Hello " + request.getName())
                                                        .build();
                                                responseObserver.onNext(reply);
                                                responseObserver.onCompleted();
                                            }
                                    )
                            )
                    )
                    .addMethod(
                            GreeterGrpc.getSayHelloAgainMethod(),
                            ServerCalls.asyncUnaryCall(
                                    (request, responseObserver) -> {
                                        ServerCalls.asyncUnimplementedUnaryCall(
                                                GreeterGrpc.getSayHelloMethod(),
                                                responseObserver
                                        );
                                    }
                            )
                    )
                    .build();
        }
    }

    private class MiddleServerCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {
        private final ServerCallHandler<ReqT, RespT> next;

        public MiddleServerCallHandler(ServerCallHandler<ReqT, RespT> next) {
            this.next = next;
        }

        @Override
        public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> call, Metadata requestHeaders) {
            logger.info("header received from client: " + requestHeaders);

            return next.startCall(new InterceptServerCall<>(call), requestHeaders);
        }
    }
}
